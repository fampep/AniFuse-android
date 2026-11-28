// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.okhttp.OkHttpDataSource
import site.anikitty.app.data.servers.FlixUnwrap
import site.anikitty.app.data.servers.FlixUnwrapDataSource
import site.anikitty.app.data.servers.GotakuObfDataSource
import site.anikitty.app.data.servers.PlaybackHttp
import site.anikitty.app.data.servers.PlayableSource
import site.anikitty.app.data.servers.SubtitleTrack
import site.anikitty.app.data.servers.SegmentStripDataSource
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

data class RelaySubtitle(
    val url: String,
    val label: String,
    val language: String?,
)

data class RelayPlayback(
    val url: String,
    val mimeType: String,
    val subtitles: List<RelaySubtitle>,
    /** Playlist bytes to serve on `/play` once the receiver is actually loaded. */
    val playlist: ByteArray?,
    /** Upstream URLs this playback may fetch. Kept off the live allowlist until publish. */
    val allowed: Set<String>,
)

/**
 * The Chromecast fetches the stream itself and cannot send Referer headers or
 * unwrap Gotaku / disguised segments. This serves a clean copy from the phone
 * on the same Wi-Fi, using the same request path as local playback.
 *
 * Child playlists and segments are addressed in the path. The Cast receiver
 * drops a `?u=` query on playlist URIs, so a query-shaped proxy never reaches
 * the CDN the phone scraped.
 */
@OptIn(UnstableApi::class)
class CastRelay(context: Context) {
    private val appContext = context.applicationContext
    private val stripBytes = AtomicInteger(SegmentStripDataSource.DEFAULT_STRIP_BYTES)
    private val obf = AtomicReference(null as site.anikitty.app.data.servers.GotakuObf?)
    private val httpFactory: OkHttpDataSource.Factory =
        PlaybackHttp.factory(emptyMap(), DEFAULT_UA)
    private val flix = AtomicReference(null as FlixUnwrap?)
    private val gotakuFactory = GotakuObfDataSource.Factory(httpFactory, obf)
    private val flixFactory = FlixUnwrapDataSource.Factory(gotakuFactory, flix)
    private val factory = SegmentStripDataSource.Factory(flixFactory, stripBytes = stripBytes)

    private val allowLock = Any()
    /** URLs the receiver is playing now, plus the previous load so a switch does not 403. */
    private var liveAllowed = emptySet<String>()
    private var priorAllowed = emptySet<String>()
    private val collecting = ThreadLocal<MutableSet<String>>()
    private var pool = newPool()
    @Volatile private var source: PlayableSource? = null
    @Volatile private var cachedPlaylist: ByteArray? = null
    /** "ts" or "fmp4" only when the playlist proves it. Null lets the receiver sniff. */
    @Volatile var segmentPack: String? = null
        private set
    @Volatile private var running = false
    private var server: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var port = 0
    private var wifiLock: WifiManager.WifiLock? = null
    private var multicast: WifiManager.MulticastLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    fun prepare(next: PlayableSource): RelayPlayback {
        val host = lanIpv4() ?: error("Connect the phone and Chromecast to the same Wi-Fi")
        source = next
        httpFactory.setUserAgent(
            next.headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value
                ?: DEFAULT_UA,
        )
        httpFactory.setDefaultRequestProperties(
            site.anikitty.app.data.servers.playbackHeaders(next.headers),
        )
        gotakuFactory.updateObf(next.gotakuObf)
        obf.set(next.gotakuObf)
        flixFactory.update(next.flix)
        flix.set(next.flix)
        val strip = when {
            next.gotakuObf != null || next.flix != null -> next.stripBytes.coerceAtLeast(0)
            next.stripBytes > 0 -> next.stripBytes
            else -> SegmentStripDataSource.DEFAULT_STRIP_BYTES
        }
        stripBytes.set(strip)
        val fresh = LinkedHashSet<String>()
        collecting.set(fresh)
        allow(next.url)
        start()
        val playlist = readPlaylist(next.url)
        val mime = when {
            playlist != null -> HLS_MIME
            next.gotakuObf != null || next.hls -> HLS_MIME
            next.url.contains(".m3u8", ignoreCase = true) -> HLS_MIME
            next.url.contains("/p/1") -> HLS_MIME
            else -> "video/mp4"
        }
        val subs = try {
            next.subtitles.mapIndexedNotNull { index, track ->
                if (!castRelaySubtitleEligible(track.url, track.mimeType)) return@mapIndexedNotNull null
                allow(track.url)
                RelaySubtitle(
                    url = "http://$host:$port/sub?i=$index",
                    label = track.label.ifBlank { "Subtitles" },
                    language = track.language,
                )
            }
        } finally {
            collecting.remove()
        }
        val file = castRelayPlaybackName(mime == HLS_MIME, segmentPack, next.audioLanguage)
        return RelayPlayback(
            url = "http://$host:$port/$file",
            mimeType = mime,
            subtitles = subs,
            playlist = playlist,
            allowed = fresh,
        )
    }

    /**
     * Point `/sub` at a caption list that arrived after the video was already
     * playing. The relay stays up, so the receiver can keep the same stream.
     */
    fun attachSubtitles(tracks: List<SubtitleTrack>): List<RelaySubtitle>? {
        if (!running || port == 0) return null
        val current = source ?: return null
        val host = lanIpv4() ?: return null
        source = current.copy(subtitles = tracks)
        return tracks.mapIndexedNotNull { index, track ->
            if (!castRelaySubtitleEligible(track.url, track.mimeType)) return@mapIndexedNotNull null
            allow(track.url)
            RelaySubtitle(
                url = "http://$host:$port/sub?i=$index",
                label = track.label.ifBlank { "Subtitles" },
                language = track.language,
            )
        }
    }

    /** Swap the served playlist and allowlist when the receiver is given this load. */
    fun publish(playback: RelayPlayback) {
        synchronized(allowLock) {
            priorAllowed = liveAllowed
            liveAllowed = playback.allowed
            cachedPlaylist = playback.playlist
        }
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        server = null
        acceptThread = null
        cachedPlaylist = null
        synchronized(allowLock) {
            liveAllowed = emptySet()
            priorAllowed = emptySet()
        }
        val retiring = pool
        pool = newPool()
        retiring.shutdownNow()
        runCatching { wifiLock?.release() }
        runCatching { multicast?.release() }
        runCatching { wakeLock?.release() }
        wifiLock = null
        multicast = null
        wakeLock = null
    }

    private fun start() {
        if (running && server?.isClosed == false) return
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress("0.0.0.0", 0))
        server = socket
        port = socket.localPort
        running = true
        holdLocks()
        acceptThread = Thread({
            while (running) {
                val client = try {
                    socket.accept()
                } catch (_: Exception) {
                    break
                }
                workers().execute { handle(client) }
            }
        }, "anikitty-cast-accept").apply {
            isDaemon = true
            start()
        }
    }

    private fun holdLocks() {
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiMode = if (android.os.Build.VERSION.SDK_INT >= 29) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            @Suppress("DEPRECATION")
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wifiLock = wifi?.createWifiLock(wifiMode, "anikitty-cast")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        multicast = wifi?.createMulticastLock("anikitty-cast")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        val power = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "anikitty:cast")?.apply {
            setReferenceCounted(false)
            acquire(4 * 60 * 60 * 1000L)
        }
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 20_000
            val header = readHeader(client)
            val request = header.lineSequence().firstOrNull().orEmpty()
            val parts = request.split(' ')
            val method = parts.getOrNull(0).orEmpty()
            if (method.equals("OPTIONS", true)) {
                writeStatus(client, 204, "text/plain", ByteArray(0), headOnly = true)
                return
            }
            if (parts.size < 2 || (!method.equals("GET", true) && !method.equals("HEAD", true))) {
                writeStatus(client, 405, "text/plain", "No".toByteArray())
                return
            }
            val headOnly = method.equals("HEAD", true)
            val target = parts[1]
            val path = target.substringBefore('?')
            val query = target.substringAfter('?', "")
            val range = rangeStart(header)
            val relayed = castRelayTarget(path)
            if (relayed != null) {
                if (!permitted(relayed)) {
                    writeStatus(client, 403, "text/plain", "No".toByteArray(), headOnly = headOnly)
                } else {
                    serveUpstream(client, relayed, range, headOnly)
                }
                return
            }
            when (path) {
                "/index.m3u8" -> servePlay(client, range, playlist = true, headOnly = headOnly)
                "/video.mp4" -> servePlay(client, range, playlist = false, headOnly = headOnly)
                "/play" -> servePlay(client, range, playlist = cachedPlaylist != null, headOnly = headOnly)
                "/u", "/u.m3u8" -> {
                    val raw = queryValue(query, "u")
                    if (raw == null || !permitted(raw)) {
                        writeStatus(client, 403, "text/plain", "No".toByteArray(), headOnly = headOnly)
                    } else {
                        serveUpstream(client, raw, range, headOnly)
                    }
                }
                "/sub" -> {
                    val index = queryValue(query, "i")?.toIntOrNull()
                    val track = index?.let { source?.subtitles?.getOrNull(it) }
                    if (track == null || !permitted(track.url)) {
                        writeStatus(client, 404, "text/plain", "No".toByteArray())
                    } else {
                        val body = readBytes(track.url)?.let { toVtt(it) }
                        if (body == null) {
                            writeStatus(client, 502, "text/plain", "No".toByteArray())
                        } else {
                            writeStatus(client, 200, "text/vtt; charset=utf-8", body)
                        }
                    }
                }
                else -> writeStatus(client, 404, "text/plain", "No".toByteArray())
            }
        }
    }

    private fun servePlay(client: Socket, range: Long?, playlist: Boolean, headOnly: Boolean) {
        val cached = cachedPlaylist
        val url = source?.url
        if (playlist && cached != null) {
            writeStatus(client, 200, HLS_MIME, cached, headOnly = headOnly)
            return
        }
        if (url == null) {
            writeStatus(client, 404, "text/plain", "No".toByteArray(), headOnly = headOnly)
            return
        }
        serveUpstream(client, url, range, headOnly)
    }

    private fun serveUpstream(client: Socket, url: String, range: Long?, headOnly: Boolean = false) {
        val position = range?.coerceAtLeast(0L) ?: 0L
        // Extensionless, image, and text-named URLs from scraped CDNs are often
        // a playlist or a decoy-wrapped segment. Read them whole, then rewrite
        // or peel. A Range on the wrapped file is not a range of the media.
        if (castRelayPlaylistUrl(url) || !castRelayRealMediaSuffix(url)) {
            serveBuffered(client, url, position, headOnly)
            return
        }
        val spec = DataSpec.Builder()
            .setUri(Uri.parse(url))
            .setPosition(position)
            .build()
        val upstream = factory.createDataSource()
        try {
            val length = upstream.open(spec)
            if (position == 0L && looksLikePlaylist(url, upstream.responseHeaders)) {
                val body = readFully(upstream, length)
                val text = castRelayPlaylistText(body)
                if (text != null) {
                    val rewritten = presentPlaylist(text, upstream.uri?.toString() ?: url)
                    writeStatus(
                        client,
                        200,
                        HLS_MIME,
                        rewritten.toByteArray(Charsets.UTF_8),
                        headOnly = headOnly,
                    )
                    return
                }
                writeBytes(
                    client,
                    200,
                    contentType(url, upstream.responseHeaders),
                    body,
                    null,
                    body.size.toLong(),
                    headOnly,
                )
                return
            }
            if (headOnly) {
                val total = if (length == C.LENGTH_UNSET.toLong()) null else position + length
                writeHead(client, if (position > 0L) 206 else 200, contentType(url, upstream.responseHeaders), if (length >= 0) length else null, position, total)
                return
            }
            val total = when {
                length == C.LENGTH_UNSET.toLong() -> null
                else -> position + length
            }
            val status = if (position > 0L) 206 else 200
            val type = contentType(url, upstream.responseHeaders)
            writeHead(client, status, type, if (length >= 0) length else null, position, total)
            val out = BufferedOutputStream(client.getOutputStream())
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = upstream.read(buf, 0, buf.size)
                if (n == C.RESULT_END_OF_INPUT || n < 0) break
                out.write(buf, 0, n)
            }
            out.flush()
        } catch (_: Exception) {
            runCatching { writeStatus(client, 502, "text/plain", "No".toByteArray()) }
        } finally {
            runCatching { upstream.close() }
        }
    }

    /**
     * Playlist or disguised segment. The upstream read always starts at byte 0
     * so a PNG/JPEG prefix can be removed before the receiver's Range is applied.
     */
    private fun serveBuffered(client: Socket, url: String, position: Long, headOnly: Boolean) {
        val upstream = factory.createDataSource()
        try {
            upstream.open(DataSpec(Uri.parse(url)))
            val body = readFully(upstream, C.LENGTH_UNSET.toLong(), limit = 32_000_000)
            val finalUrl = upstream.uri?.toString() ?: url
            val text = castRelayPlaylistText(body)
            if (text != null) {
                val rewritten = presentPlaylist(text, finalUrl)
                writeStatus(
                    client,
                    200,
                    HLS_MIME,
                    rewritten.toByteArray(Charsets.UTF_8),
                    headOnly = headOnly,
                )
                return
            }
            val media = castRelayUnwrapSegment(body)
            writeSlice(client, media, castRelayUnwrappedType(media, finalUrl, segmentPack), position, headOnly)
        } catch (_: Exception) {
            runCatching { writeStatus(client, 502, "text/plain", "No".toByteArray()) }
        } finally {
            runCatching { upstream.close() }
        }
    }

    private fun writeSlice(
        client: Socket,
        body: ByteArray,
        type: String,
        position: Long,
        headOnly: Boolean,
    ) {
        if (position > 0L && position >= body.size) {
            writeStatus(client, 416, "text/plain", ByteArray(0), headOnly = true)
            return
        }
        val start = position.toInt().coerceAtLeast(0)
        val slice = if (start == 0) body else body.copyOfRange(start, body.size)
        val status = if (start > 0) 206 else 200
        writeBytes(client, status, type, slice, start.toLong(), body.size.toLong(), headOnly)
    }

    private fun readPlaylist(url: String): ByteArray? {
        val upstream = factory.createDataSource()
        return try {
            upstream.open(DataSpec(Uri.parse(url)))
            val hinted = looksLikePlaylist(url, upstream.responseHeaders)
            val peek = ByteArray(16)
            val n = readInto(upstream, peek)
            val head = if (n > 0) String(peek, 0, n, Charsets.UTF_8) else ""
            val gzip = n >= 2 && peek[0] == 0x1f.toByte() && peek[1] == 0x8b.toByte()
            if (!hinted && !head.startsWith("#EXTM3U") && !head.startsWith("\uFEFF#EXT") && !gzip) {
                return null
            }
            val rest = readFully(upstream, C.LENGTH_UNSET.toLong())
            val body = ByteArray(n + rest.size)
            if (n > 0) System.arraycopy(peek, 0, body, 0, n)
            System.arraycopy(rest, 0, body, n, rest.size)
            val text = castRelayPlaylistText(body) ?: return null
            var playlist = text
            var base = upstream.uri?.toString() ?: url
            val sole = castRelaySoleVariant(playlist)
            if (sole != null) {
                val abs = resolve(runCatching { URI(base) }.getOrNull(), sole)
                allow(abs)
                val nested = fetchText(abs)
                if (nested != null && !nested.contains("#EXT-X-STREAM-INF", ignoreCase = true)) {
                    playlist = nested
                    base = abs
                }
            }
            segmentPack = classifyPack(playlist, base)
            presentPlaylist(playlist, base).toByteArray(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { upstream.close() }
        }
    }

    private fun presentPlaylist(text: String, baseUrl: String): String =
        castRelayPreferAudio(rewritePlaylist(text, baseUrl), source?.audioLanguage)

    private fun rewritePlaylist(text: String, baseUrl: String): String =
        castRelayRewritePlaylist(text, baseUrl) { absolute, kind ->
            allow(absolute)
            proxy(absolute, kind)
        }

    private fun proxy(absolute: String, kind: CastRelayLink): String {
        val host = lanIpv4() ?: return absolute
        val payload = castRelayEncodeTarget(absolute)
        val file = castRelayProxyFile(absolute, kind, segmentPack)
        return "http://$host:$port/m/$payload/$file"
    }

    private fun resolve(base: URI?, ref: String): String {
        if (base == null) return ref
        return runCatching { base.resolve(ref).toString() }.getOrDefault(ref)
    }

    private fun allow(url: String) {
        val set = collecting.get()
        if (set != null) {
            if (set.size < 8_000) set.add(url)
            return
        }
        // A variant playlist is rewritten when the TV asks for it, after publish.
        synchronized(allowLock) {
            if (url in liveAllowed || url in priorAllowed || liveAllowed.size >= 8_000) return
            liveAllowed = liveAllowed + url
        }
    }

    private fun permitted(url: String): Boolean = synchronized(allowLock) {
        url in liveAllowed || url in priorAllowed
    }

    private fun workers(): java.util.concurrent.ExecutorService {
        if (pool.isShutdown) pool = newPool()
        return pool
    }

    private fun newPool() = Executors.newFixedThreadPool(6) { runnable ->
        Thread(runnable, "anikitty-cast").apply { isDaemon = true }
    }

    private fun classifyPack(text: String, baseUrl: String, depth: Int = 0): String? {
        certainHlsPack(text)?.let { return it }
        val base = runCatching { URI(baseUrl) }.getOrNull()
        if (depth == 0 && text.lowercase().contains("#ext-x-stream-inf")) {
            val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            if (line != null) {
                val abs = resolve(base, line)
                val nested = fetchText(abs)
                if (nested != null && nested.trimStart().startsWith("#EXTM3U")) {
                    return classifyPack(nested, abs, depth + 1)
                }
            }
        }
        // Extensionless and disguised segments never name .ts or .mp4. The
        // receiver then treats every provider as MPEG-TS and refuses fMP4.
        if (!text.lowercase().contains("#ext-x-stream-inf")) {
            val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            if (line != null) {
                val head = peekBytes(resolve(base, line), 128 * 1024)
                if (head != null) {
                    castRelayPackFromBytes(castRelayUnwrapSegment(head))?.let { return it }
                }
            }
        }
        return null
    }

    private fun peekBytes(url: String, count: Int): ByteArray? {
        if (count <= 0) return null
        val upstream = factory.createDataSource()
        return try {
            upstream.open(DataSpec(Uri.parse(url)))
            val buf = ByteArray(count)
            val n = readInto(upstream, buf)
            if (n <= 0) null else buf.copyOf(n)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { upstream.close() }
        }
    }

    private fun fetchText(url: String): String? {
        val upstream = factory.createDataSource()
        return try {
            upstream.open(DataSpec(Uri.parse(url)))
            castRelayPlaylistText(readFully(upstream, C.LENGTH_UNSET.toLong()))
        } catch (_: Exception) {
            null
        } finally {
            runCatching { upstream.close() }
        }
    }

    private fun looksLikePlaylist(url: String, headers: Map<String, List<String>>): Boolean =
        castRelayLooksLikePlaylist(url, headers)

    private fun contentType(url: String, headers: Map<String, List<String>>): String =
        castRelaySegmentType(url, castRelayHeader(headers, "Content-Type"), segmentPack)

    private fun readBytes(url: String): ByteArray? {
        val trimmed = url.trim()
        if (trimmed.startsWith("file:", ignoreCase = true)) {
            val path = Uri.parse(trimmed).path ?: return null
            return runCatching { java.io.File(path).readBytes() }.getOrNull()
        }
        if (trimmed.startsWith("content:", ignoreCase = true)) {
            return runCatching {
                appContext.contentResolver.openInputStream(Uri.parse(trimmed))?.use { it.readBytes() }
            }.getOrNull()
        }
        val upstream = factory.createDataSource()
        return try {
            val length = upstream.open(DataSpec(Uri.parse(trimmed)))
            readFully(upstream, length)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { upstream.close() }
        }
    }

    private fun readFully(
        upstream: androidx.media3.datasource.DataSource,
        declared: Long,
        limit: Int = 8_000_000,
    ): ByteArray {
        val out = ByteArrayOutputStream(
            if (declared in 1..(2_000_000)) declared.toInt() else 64 * 1024,
        )
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (total < limit) {
            val n = upstream.read(buf, 0, buf.size)
            if (n == C.RESULT_END_OF_INPUT || n < 0) break
            out.write(buf, 0, n)
            total += n
        }
        return out.toByteArray()
    }

    private fun readInto(upstream: androidx.media3.datasource.DataSource, buf: ByteArray): Int {
        var got = 0
        while (got < buf.size) {
            val n = upstream.read(buf, got, buf.size - got)
            if (n == C.RESULT_END_OF_INPUT || n < 0) break
            got += n
        }
        return got
    }

    private fun toVtt(bytes: ByteArray): ByteArray {
        val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        if (text.trimStart().startsWith("WEBVTT")) return text.toByteArray(Charsets.UTF_8)
        val converted = "WEBVTT\n\n" + TIME_COMMA.replace(text, "$1.$2")
        return converted.toByteArray(Charsets.UTF_8)
    }

    private fun readHeader(socket: Socket): String {
        val input = socket.getInputStream()
        val out = ByteArrayOutputStream()
        var matched = 0
        val needle = byteArrayOf(13, 10, 13, 10)
        while (out.size() < 8_192) {
            val b = input.read()
            if (b < 0) break
            out.write(b)
            matched = if (b == needle[matched].toInt()) matched + 1 else 0
            if (matched == needle.size) break
        }
        return out.toString(Charsets.ISO_8859_1.name())
    }

    private fun rangeStart(header: String): Long? {
        val line = header.lineSequence().firstOrNull { it.startsWith("Range:", true) } ?: return null
        val value = line.substringAfter(':').trim()
        if (!value.startsWith("bytes=", true)) return null
        return value.substringAfter('=').substringBefore('-').toLongOrNull()
    }

    private fun queryValue(query: String, key: String): String? {
        for (part in query.split('&')) {
            val name = part.substringBefore('=')
            if (name == key) {
                return URLDecoder.decode(part.substringAfter('='), Charsets.UTF_8.name())
            }
        }
        return null
    }

    private fun writeStatus(
        client: Socket,
        status: Int,
        type: String,
        body: ByteArray,
        headOnly: Boolean = false,
    ) {
        writeBytes(client, status, type, body, null, body.size.toLong(), headOnly)
    }

    private fun writeBytes(
        client: Socket,
        status: Int,
        type: String,
        body: ByteArray,
        position: Long?,
        total: Long?,
        headOnly: Boolean = false,
    ) {
        writeHead(client, status, type, body.size.toLong(), position ?: 0L, total)
        if (!headOnly) {
            client.getOutputStream().write(body)
            client.getOutputStream().flush()
        }
    }

    private fun writeHead(
        client: Socket,
        status: Int,
        type: String,
        length: Long?,
        position: Long,
        total: Long?,
    ) {
        val reason = when (status) {
            206 -> "Partial Content"
            204 -> "No Content"
            200 -> "OK"
            else -> "Error"
        }
        val builder = StringBuilder()
        builder.append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n")
        builder.append("Content-Type: ").append(type).append("\r\n")
        for (line in castRelayCorsLines()) {
            builder.append(line).append("\r\n")
        }
        builder.append("Accept-Ranges: bytes\r\n")
        builder.append("Connection: close\r\n")
        if (length != null && length >= 0) {
            builder.append("Content-Length: ").append(length).append("\r\n")
        }
        if (status == 206 && length != null && total != null) {
            val end = position + length - 1
            builder.append("Content-Range: bytes ").append(position).append('-').append(end)
                .append('/').append(total).append("\r\n")
        }
        builder.append("\r\n")
        client.getOutputStream().write(builder.toString().toByteArray(Charsets.ISO_8859_1))
    }

    private fun lanIpv4(): String? = wifiIpv4() ?: interfaceIpv4()

    private fun wifiIpv4(): String? {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        val onLan = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        if (!onLan) return null
        val props = cm.getLinkProperties(network) ?: return null
        val found = ArrayList<Inet4Address>()
        for (link in props.linkAddresses) {
            val addr = link.address
            if (addr is Inet4Address && isPrivate(addr)) found += addr
        }
        return preferPrivate(found)
    }

    private fun interfaceIpv4(): String? {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        val found = ArrayList<Inet4Address>()
        for (nic in interfaces) {
            if (!runCatching { nic.isUp && !nic.isLoopback }.getOrDefault(false)) continue
            for (addr in nic.inetAddresses) {
                if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress && isPrivate(addr)) {
                    found += addr
                }
            }
        }
        return preferPrivate(found)
    }

    private fun preferPrivate(found: List<Inet4Address>): String? {
        return found.minByOrNull { address ->
            val first = address.address[0].toInt() and 0xff
            when (first) {
                192 -> 0
                10 -> 1
                else -> 2
            }
        }?.hostAddress
    }

    private fun isPrivate(addr: Inet4Address): Boolean {
        val b = addr.address
        val a = b[0].toInt() and 0xff
        val c = b[1].toInt() and 0xff
        return a == 10 || (a == 192 && c == 168) || (a == 172 && c in 16..31)
    }

    companion object {
        private const val HLS_MIME = "application/vnd.apple.mpegurl"
        private const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
        private val TIME_COMMA = Regex("""(\d{2}:\d{2}:\d{2}),(\d{3})""")
    }
}

/**
 * CORS for a public HTTPS receiver (the Default Media Receiver) fetching this
 * phone on the LAN. Private Network Access and Local Network Access both have
 * to be allowed on every response, including the OPTIONS preflight.
 */
internal fun castRelayCorsLines(): List<String> = listOf(
    "Access-Control-Allow-Origin: *",
    "Access-Control-Allow-Private-Network: true",
    "Access-Control-Allow-Local-Network: true",
    "Access-Control-Allow-Methods: GET, HEAD, OPTIONS",
    "Access-Control-Allow-Headers: Content-Type, Range, Accept, Origin",
    "Access-Control-Expose-Headers: Content-Length, Content-Range, Content-Type, Accept-Ranges",
    "Access-Control-Max-Age: 86400",
)

/**
 * File the receiver opens. The language is part of the query so a Sub/Dub
 * switch is a new URL and Chromecast refetches the master instead of replaying
 * the cached default rendition.
 */
internal fun castRelayPlaybackName(hls: Boolean, segmentPack: String?, audioLanguage: String?): String {
    if (!hls) return "video.mp4"
    return "index.m3u8${castRelayPlaybackQuery(segmentPack, audioLanguage)}"
}

/**
 * Address Chromecast should open. `pack` stays a segment-format hint and is
 * not part of the fetch. `lang` stays on the URL so Sub and Dub are different
 * media items and the receiver cannot reuse the previous master.
 */
internal fun castRelayContent(raw: String): CastRelayContent {
    val query = raw.substringAfter('?', "")
    val pack = queryValue(query, "pack")?.takeIf { it == "ts" || it == "fmp4" }
    val lang = queryValue(query, "lang")?.takeIf { value ->
        value.length == 2 && value.all { it in 'a'..'z' }
    }
    val base = raw.substringBefore('?')
    val url = if (lang == null) base else "$base?lang=$lang"
    return CastRelayContent(url = url, pack = pack)
}

internal data class CastRelayContent(val url: String, val pack: String?)

private fun queryValue(query: String, name: String): String? {
    for (part in query.split('&')) {
        val key = part.substringBefore('=')
        if (key == name) return part.substringAfter('=', "").takeIf { it.isNotEmpty() }
    }
    return null
}

internal fun castRelayPlaybackQuery(segmentPack: String?, audioLanguage: String?): String {
    val parts = ArrayList<String>(2)
    segmentPack?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += "pack=$it" }
    audioLanguage
        ?.trim()
        ?.lowercase()
        ?.substringBefore('-')
        ?.takeIf { it.length == 2 && it.all { char -> char in 'a'..'z' } }
        ?.let { parts += "lang=$it" }
    return if (parts.isEmpty()) "" else parts.joinToString("&", prefix = "?")
}

/**
 * AnimeX names the master `deeznuts.txt` and each variant `nut-1.txt`.
 * xanime.app names each variant `a-index-….ico`.
 * Those are playlists. Media segments stay `.jpg`, `.js`, or `.gif`.
 */
internal fun castRelayPlaylistUrl(url: String): Boolean {
    val path = url.substringBefore('?').lowercase()
    if (path.contains(".m3u8") || path.contains("/p/1")) return true
    val file = path.substringAfterLast('/')
    return file == "deeznuts.txt" ||
        (file.startsWith("nut-") && file.endsWith(".txt")) ||
        (file.startsWith("a-index-") && file.endsWith(".ico"))
}

internal fun castRelayHeader(headers: Map<String, List<String>>, name: String): String {
    val values = headers[name]
        ?: headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    return values?.joinToString(",")?.substringBefore(';')?.trim().orEmpty()
}

/**
 * Move DEFAULT=YES onto the audio rendition the Sub / Dub control asked for.
 * Chromecast plays the default rendition and ignores the phone's track selector.
 * A playlist with no matching language is left as the site sent it.
 */
internal fun castRelayPreferAudio(playlist: String, language: String?): String {
    val want = language
        ?.trim()
        ?.lowercase()
        ?.substringBefore('-')
        ?.takeIf { it.length == 2 }
        ?: return playlist
    if (!playlist.contains("#EXT-X-MEDIA")) return playlist
    val lines = playlist.lines()
    val audio = ArrayList<RelayAudioLine>(4)
    lines.forEachIndexed { index, line ->
        val trimmed = line.trimStart()
        if (!trimmed.startsWith("#EXT-X-MEDIA") || !trimmed.contains("TYPE=AUDIO")) return@forEachIndexed
        audio += RelayAudioLine(
            index = index,
            group = castRelayAttr(line, "GROUP-ID").orEmpty(),
            code = castRelayAudioCode(line),
        )
    }
    if (audio.isEmpty()) return playlist
    val chosen = HashSet<Int>()
    val touched = HashSet<Int>()
    for (group in audio.groupBy { it.group }.values) {
        val matches = group.filter { it.code == want }
        if (matches.isEmpty()) continue
        val pick = matches.firstOrNull { lines[it.index].contains("DEFAULT=YES") } ?: matches.first()
        chosen += pick.index
        group.forEach { touched += it.index }
    }
    if (chosen.isEmpty()) return playlist
    return lines.mapIndexed { index, line ->
        if (index !in touched) line else castRelaySetDefault(line, index in chosen)
    }.joinToString("\n")
}

private data class RelayAudioLine(val index: Int, val group: String, val code: String?)

private val CAST_AUDIO_CODES = mapOf(
    "en" to "en", "eng" to "en",
    "ja" to "ja", "jpn" to "ja",
    "es" to "es", "spa" to "es",
    "fr" to "fr", "fra" to "fr", "fre" to "fr",
    "de" to "de", "deu" to "de", "ger" to "de",
    "pt" to "pt", "por" to "pt",
    "it" to "it", "ita" to "it",
    "ko" to "ko", "kor" to "ko",
    "zh" to "zh", "zho" to "zh", "chi" to "zh",
)

private val CAST_AUDIO_NAMES = listOf(
    "en" to listOf("english"),
    "ja" to listOf("japanese", "native"),
    "es" to listOf("spanish", "español", "espanol"),
)

private fun castRelayAudioCode(line: String): String? {
    val lang = castRelayAttr(line, "LANGUAGE")
    if (lang != null) {
        val key = lang.lowercase().substringBefore('-')
        return CAST_AUDIO_CODES[key] ?: key.takeIf { it.length == 2 }
    }
    val name = castRelayAttr(line, "NAME")?.lowercase() ?: return null
    return CAST_AUDIO_NAMES.firstOrNull { (_, words) -> words.any { name.contains(it) } }?.first
}

private fun castRelayAttr(line: String, name: String): String? {
    val match = Regex("""$name="([^"]*)"""", RegexOption.IGNORE_CASE).find(line) ?: return null
    return match.groupValues[1].trim().ifEmpty { null }
}

private val CAST_DEFAULT_ATTR = Regex("""DEFAULT=(YES|NO)""", RegexOption.IGNORE_CASE)

private fun castRelaySetDefault(line: String, yes: Boolean): String {
    val value = if (yes) "YES" else "NO"
    return if (CAST_DEFAULT_ATTR.containsMatchIn(line)) {
        CAST_DEFAULT_ATTR.replace(line, "DEFAULT=$value")
    } else if (yes) {
        "$line,DEFAULT=YES"
    } else {
        line
    }
}

internal fun castRelayLooksLikePlaylist(url: String, headers: Map<String, List<String>>): Boolean {
    if (castRelayPlaylistUrl(url)) return true
    val type = castRelayHeader(headers, "Content-Type").lowercase()
    return type.contains("mpegurl") || type.contains("m3u")
}

/**
 * Container for a segment the playlist does not name.
 * MPEG-TS starts with the sync byte. fMP4 starts with an `ftyp` box.
 */
internal fun castRelayPackFromBytes(header: ByteArray): String? {
    if (header.isNotEmpty() && header[0] == 0x47.toByte()) return "ts"
    if (header.size >= 8) {
        val box = String(header, 4, 4, Charsets.ISO_8859_1)
        if (box == "ftyp") return "fmp4"
    }
    return null
}

/**
 * Type the receiver should see. A sniffed fMP4 pack wins over a CDN that
 * labels every segment `application/octet-stream` or `video/mp2t`.
 */
internal fun castRelaySegmentType(url: String, contentType: String, pack: String?): String {
    if (castRelayPlaylistUrl(url)) return "application/vnd.apple.mpegurl"
    val path = url.substringBefore('?').lowercase()
    when {
        path.contains(".m4s") || path.contains(".mp4") || path.contains(".m4a") || path.contains(".cmf") ->
            return "video/mp4"
        path.contains(".aac") -> return "audio/aac"
        path.contains(".vtt") -> return "text/vtt"
        path.contains(".ts") || path.contains("/s/1") -> return "video/mp2t"
    }
    val raw = contentType.substringBefore(';').trim()
    val type = raw.lowercase()
    if (type.contains("mpegurl") || type.contains("m3u")) return "application/vnd.apple.mpegurl"
    if (type.startsWith("audio/") || type.startsWith("text/")) return raw
    if (pack == "fmp4") return "video/mp4"
    if (pack == "ts") return "video/mp2t"
    if (type.startsWith("video/")) return raw
    if (type.startsWith("image/") || type.isBlank() || type.contains("octet-stream")) return "video/mp2t"
    return raw.ifBlank { "video/mp2t" }
}

/**
 * A file the receiver can stream with Range intact. Scraped CDNs hide playlists
 * and segments behind other suffixes, and those have to be read whole first.
 */
internal fun castRelayRealMediaSuffix(url: String): Boolean {
    val path = url.substringBefore('?').lowercase()
    return path.endsWith(".ts") ||
        path.endsWith(".m4s") ||
        path.endsWith(".mp4") ||
        path.endsWith(".m4v") ||
        path.endsWith(".aac") ||
        path.endsWith(".m4a") ||
        path.endsWith(".cmfv") ||
        path.endsWith(".cmfa") ||
        path.endsWith(".cmf") ||
        path.endsWith(".vtt") ||
        path.endsWith(".srt")
}

/** Upstream URL carried in `/m/<payload>/<file>`. Query strings are not used. */
internal fun castRelayTarget(path: String): String? {
    if (!path.startsWith("/m/")) return null
    val rest = path.removePrefix("/m/")
    val payload = rest.substringBefore('/', "")
    if (payload.isEmpty() || payload == rest) return null
    return castRelayDecodeTarget(payload)
}

internal fun castRelayEncodeTarget(url: String): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(url.toByteArray(Charsets.UTF_8))

internal fun castRelayDecodeTarget(payload: String): String? {
    if (payload.isEmpty() || payload.length > 12_000) return null
    if (!payload.all { it.isLetterOrDigit() || it == '-' || it == '_' }) return null
    val padded = when (payload.length % 4) {
        0 -> payload
        2 -> "$payload=="
        3 -> "$payload="
        else -> return null
    }
    val url = runCatching {
        String(Base64.getUrlDecoder().decode(padded), Charsets.UTF_8)
    }.getOrNull() ?: return null
    return url.takeIf { it.startsWith("https://") || it.startsWith("http://") }
}

internal enum class CastRelayLink { PLAYLIST, SEGMENT, TEXT }

/** True when the relay can fetch this caption URL for the Chromecast. */
internal fun castRelayCaptionFetchable(url: String): Boolean {
    val trimmed = url.trim()
    return trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("file:", ignoreCase = true) ||
        trimmed.startsWith("content:", ignoreCase = true)
}

/**
 * Library captions often use `application/x-subrip` without a `.srt` path.
 * Accept those plus classic VTT/SRT URLs so Cast does not drop the sidecar.
 */
internal fun castRelaySubtitleEligible(url: String, mimeType: String?): Boolean {
    if (!castRelayCaptionFetchable(url)) return false
    val lower = url.lowercase()
    val mime = mimeType.orEmpty().lowercase()
    return lower.contains(".vtt") ||
        lower.contains(".srt") ||
        "vtt" in mime ||
        "subrip" in mime ||
        "srt" in mime ||
        mime == "text/plain"
}

internal fun castRelayProxyFile(url: String, kind: CastRelayLink, pack: String?): String {
    val path = url.substringBefore('?').lowercase()
    if (kind == CastRelayLink.TEXT || path.endsWith(".vtt") || path.endsWith(".srt")) return "subs.vtt"
    if (kind == CastRelayLink.PLAYLIST || castRelayPlaylistUrl(url)) return "index.m3u8"
    if (
        pack == "fmp4" ||
        path.endsWith(".m4s") ||
        path.endsWith(".mp4") ||
        path.endsWith(".m4v") ||
        path.endsWith(".cmf") ||
        path.endsWith(".cmfv")
    ) {
        return "seg.mp4"
    }
    if (path.endsWith(".aac") || path.endsWith(".m4a") || path.endsWith(".cmfa")) return "seg.aac"
    if (path.endsWith(".key")) return "key.bin"
    return "seg.ts"
}

/**
 * One video rendition and no separate audio group. The receiver then gets the
 * media playlist itself, instead of a master whose only child it fails to follow.
 */
internal fun castRelaySoleVariant(text: String): String? {
    if (text.contains("#EXT-X-MEDIA", ignoreCase = true)) return null
    var count = 0
    var uri: String? = null
    var pending = false
    for (raw in text.lineSequence()) {
        val line = raw.trim()
        if (line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) {
            count += 1
            pending = true
            continue
        }
        if (pending && line.isNotEmpty() && !line.startsWith("#")) {
            if (uri == null) uri = line
            pending = false
        }
    }
    if (count != 1) return null
    return uri
}

internal fun castRelayRewritePlaylist(
    text: String,
    baseUrl: String,
    link: (absolute: String, kind: CastRelayLink) -> String,
): String {
    val base = runCatching { URI(baseUrl) }.getOrNull()
    var expectPlaylist = false
    val rewritten = text.lineSequence().joinToString("\n") { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@joinToString line.removeSuffix("\r")
        if (trimmed.startsWith("#")) {
            if (
                trimmed.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) ||
                trimmed.startsWith("#EXT-X-I-FRAME-STREAM-INF", ignoreCase = true)
            ) {
                expectPlaylist = true
            }
            castRelayRewriteUriAttrs(line.removeSuffix("\r"), base, link)
        } else {
            val abs = castRelayResolve(base, trimmed)
            val kind = when {
                expectPlaylist || castRelayPlaylistUrl(abs) -> CastRelayLink.PLAYLIST
                castRelayTextUrl(abs) -> CastRelayLink.TEXT
                else -> CastRelayLink.SEGMENT
            }
            expectPlaylist = false
            link(abs, kind)
        }
    }
    return if (rewritten.endsWith("\n")) rewritten else "$rewritten\n"
}

private val CAST_URI_QUOTED = Regex("""URI="([^"]*)"""")
private val CAST_URI_BARE = Regex("""URI=([^",\s]+)""")

private fun castRelayRewriteUriAttrs(
    line: String,
    base: URI?,
    link: (absolute: String, kind: CastRelayLink) -> String,
): String {
    val kind = castRelayTagKind(line)
    val quoted = CAST_URI_QUOTED.replace(line) { match ->
        val raw = match.groupValues[1]
        if (raw.startsWith("data:") || raw.startsWith("skd:")) {
            match.value
        } else {
            val abs = castRelayResolve(base, raw)
            "URI=\"${link(abs, castRelayUriKind(kind, abs))}\""
        }
    }
    if (quoted.contains("URI=\"")) return quoted
    return CAST_URI_BARE.replace(quoted) { match ->
        val raw = match.groupValues[1]
        if (raw.startsWith("data:") || raw.startsWith("skd:")) {
            match.value
        } else {
            val abs = castRelayResolve(base, raw)
            "URI=\"${link(abs, castRelayUriKind(kind, abs))}\""
        }
    }
}

private fun castRelayTagKind(line: String): CastRelayLink {
    val upper = line.trimStart().uppercase()
    if (upper.contains("TYPE=SUBTITLES") || upper.contains("TYPE=CLOSED-CAPTIONS")) {
        return CastRelayLink.TEXT
    }
    if (
        upper.startsWith("#EXT-X-MEDIA") ||
        upper.startsWith("#EXT-X-I-FRAME-STREAM-INF") ||
        upper.startsWith("#EXT-X-STREAM-INF")
    ) {
        return CastRelayLink.PLAYLIST
    }
    return CastRelayLink.SEGMENT
}

private fun castRelayUriKind(tag: CastRelayLink, url: String): CastRelayLink = when {
    castRelayTextUrl(url) -> CastRelayLink.TEXT
    castRelayPlaylistUrl(url) -> CastRelayLink.PLAYLIST
    else -> tag
}

private fun castRelayTextUrl(url: String): Boolean {
    val path = url.substringBefore('?').lowercase()
    return path.endsWith(".vtt") || path.endsWith(".srt") || path.endsWith(".ass") || path.endsWith(".ssa")
}

private fun castRelayResolve(base: URI?, ref: String): String {
    if (base == null) return ref
    return runCatching { base.resolve(ref).toString() }.getOrDefault(ref)
}

/** Clear HLS text, or null when [bytes] are media. Gzip playlists are inflated first. */
internal fun castRelayPlaylistText(bytes: ByteArray): String? {
    if (bytes.isEmpty() || bytes.size > 8_000_000) return null
    val plain = if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
        runCatching {
            GZIPInputStream(ByteArrayInputStream(bytes)).use { gzip ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8 * 1024)
                var total = 0
                while (total < 8_000_000) {
                    val n = gzip.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                }
                out.toByteArray()
            }
        }.getOrNull() ?: return null
    } else {
        bytes
    }
    if (plain.size > 8_000_000) return null
    val text = plain.toString(Charsets.UTF_8).removePrefix("\uFEFF")
    if (!text.trimStart().startsWith("#EXTM3U")) return null
    return text
}

/**
 * Drop a JPEG, PNG, or ICO prefix scraped CDNs stick in front of a segment.
 * The Cast receiver will not search for the sync byte itself.
 */
internal fun castRelayUnwrapSegment(seg: ByteArray, scanLimit: Int = 128 * 1024): ByteArray {
    if (seg.size < 12) return seg
    if (seg[0] == 0x47.toByte() && castRelayTsSync(seg, 0)) return seg
    if (castRelayIsoBox(seg, 0)) return seg
    val limit = minOf(seg.size, scanLimit.coerceAtLeast(12))
    val packet = 188
    val tsEnd = limit - packet * 3
    var i = 1
    while (i <= tsEnd) {
        if (seg[i] == 0x47.toByte() && castRelayTsSync(seg, i)) return seg.copyOfRange(i, seg.size)
        i += 1
    }
    var box = 4
    while (box + 4 <= limit) {
        if (castRelayIsoBox(seg, box - 4)) return seg.copyOfRange(box - 4, seg.size)
        box += 1
    }
    return seg
}

private fun castRelayTsSync(seg: ByteArray, start: Int): Boolean {
    val packet = 188
    if (start < 0 || start + packet * 3 > seg.size) return false
    return seg[start] == 0x47.toByte() &&
        seg[start + packet] == 0x47.toByte() &&
        seg[start + packet * 2] == 0x47.toByte()
}

private fun castRelayIsoBox(seg: ByteArray, start: Int): Boolean {
    if (start < 0 || start + 8 > seg.size) return false
    val type = String(seg, start + 4, 4, Charsets.ISO_8859_1)
    return type == "ftyp" || type == "styp" || type == "moof" || type == "moov" || type == "sidx"
}

/** Type to advertise after a decoy prefix is gone. Magic bytes win over the URL. */
internal fun castRelayUnwrappedType(media: ByteArray, url: String, pack: String?): String {
    return when (castRelayPackFromBytes(media)) {
        "fmp4" -> "video/mp4"
        "ts" -> "video/mp2t"
        else -> castRelaySegmentType(url, "", pack)
    }
}

/** "fmp4" or "ts" when this playlist names its segments. Null when it does not. */
internal fun certainHlsPack(text: String): String? {
    val lower = text.lowercase()
    if (
        lower.contains("#ext-x-map") ||
        lower.contains(".m4s") ||
        lower.contains(".cmf") ||
        lower.contains(".m4a") ||
        lower.contains(".m4v") ||
        lower.contains(".mp4")
    ) {
        return "fmp4"
    }
    if (lower.contains(".ts")) return "ts"
    return null
}
