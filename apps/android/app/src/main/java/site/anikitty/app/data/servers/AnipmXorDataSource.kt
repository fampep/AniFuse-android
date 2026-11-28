// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AnimePapa / ani.pm PNG HLS segments: after the BYTERANGE (or IEND) cut,
 * payload bytes are XOR'd with a one-byte key. The site's XorLoader detects
 * the key from the MPEG-TS sync byte `0x47` and feeds clear TS to hls.js —
 * Media3 needs the same unwrap or demux fails with "Cannot find sync byte".
 *
 * BYTERANGE playlists already start past the PNG header — honor Media3's
 * ranged [DataSpec] and stream-XOR on read so first frame does not wait on
 * a full-object download. Full PNG responses (position 0) still buffer +
 * IEND-cut + unwrap.
 */
@OptIn(UnstableApi::class)
class AnipmXorDataSource(
    private val upstream: DataSource,
    private val enabled: AtomicBoolean,
) : DataSource {
    private var body: ByteArray? = null
    private var bodyPos = 0
    private var mode = Mode.PASSTHROUGH
    private var openedUri: Uri? = null
    private var streamKey = 0
    private var streamKeyReady = false

    private enum class Mode { PASSTHROUGH, BUFFER, STREAM_XOR }

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        body = null
        bodyPos = 0
        mode = Mode.PASSTHROUGH
        openedUri = dataSpec.uri
        streamKey = 0
        streamKeyReady = false
        if (!enabled.get() || isPlaylistOrText(dataSpec.uri)) {
            return upstream.open(dataSpec)
        }
        // Media3 HLS BYTERANGE — Range into the PNG trailer is already XOR'd
        // TS. Pass the spec through so OkHttp sends Range; XOR on read.
        if (anipmXorUsesStreamByterange(dataSpec.position)) {
            mode = Mode.STREAM_XOR
            return upstream.open(dataSpec)
        }
        // Cold open at offset 0 (no BYTERANGE / probe) — full PNG + IEND cut.
        val full = dataSpec.buildUpon()
            .setPosition(0L)
            .setLength(C.LENGTH_UNSET.toLong())
            .build()
        val opened = upstream.open(full)
        val raw = readFully(opened)
        runCatching { upstream.close() }
        val plain = anipmXorUnwrap(raw)
        body = plain
        bodyPos = 0
        mode = Mode.BUFFER
        return plain.size.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length <= 0) return 0
        when (mode) {
            Mode.PASSTHROUGH -> return upstream.read(buffer, offset, length)
            Mode.BUFFER -> {
                val buf = body ?: return C.RESULT_END_OF_INPUT
                if (bodyPos >= buf.size) return C.RESULT_END_OF_INPUT
                val n = minOf(length, buf.size - bodyPos)
                System.arraycopy(buf, bodyPos, buffer, offset, n)
                bodyPos += n
                return n
            }
            Mode.STREAM_XOR -> {
                val n = upstream.read(buffer, offset, length)
                if (n <= 0) return n
                if (!streamKeyReady) {
                    streamKey = anipmXorStreamKey(buffer, offset, n)
                    streamKeyReady = true
                }
                if (streamKey != 0) {
                    for (i in 0 until n) {
                        buffer[offset + i] =
                            (buffer[offset + i].toInt() xor streamKey).toByte()
                    }
                }
                return n
            }
        }
    }

    override fun getUri(): Uri? = upstream.uri ?: openedUri

    override fun getResponseHeaders(): Map<String, List<String>> = when (mode) {
        // Clear MPEG-TS — never advertise image/* from the PNG CDN response.
        Mode.BUFFER, Mode.STREAM_XOR -> mapOf("Content-Type" to listOf("video/mp2t"))
        Mode.PASSTHROUGH -> upstream.responseHeaders
    }

    override fun close() {
        body = null
        bodyPos = 0
        mode = Mode.PASSTHROUGH
        openedUri = null
        streamKey = 0
        streamKeyReady = false
        runCatching { upstream.close() }
    }

    private fun isPlaylistOrText(uri: Uri): Boolean {
        val path = uri.path.orEmpty().lowercase()
        return path.contains(".m3u8") ||
            path.contains(".direct.pls") ||
            path.endsWith(".vtt") ||
            path.endsWith(".srt") ||
            path.endsWith(".ass") ||
            path.endsWith(".ssa") ||
            path.contains("subtitle")
    }

    private fun readFully(declaredLength: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream(
            when {
                declaredLength > 0 -> declaredLength.toInt().coerceAtMost(8_000_000)
                else -> 64 * 1024
            },
        )
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = upstream.read(buf, 0, buf.size)
            if (n == C.RESULT_END_OF_INPUT || n < 0) break
            out.write(buf, 0, n)
            if (out.size() > 40_000_000) break
        }
        return out.toByteArray()
    }

    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val enabled: AtomicBoolean = AtomicBoolean(false),
    ) : DataSource.Factory {
        fun setEnabled(value: Boolean) {
            enabled.set(value)
        }

        override fun createDataSource(): DataSource =
            AnipmXorDataSource(upstreamFactory.createDataSource(), enabled)
    }
}

/** True when Media3 already ranged past the PNG header into XOR'd TS. */
internal fun anipmXorUsesStreamByterange(position: Long): Boolean = position > 0L

/**
 * Stream-path XOR key from the first ranged chunk. Prefer the 188-byte sync
 * check when the first read is long enough; otherwise use the first-byte
 * candidate (XorLoader's 0xFF / 0x5A / 0x00 fast path).
 */
internal fun anipmXorStreamKey(buffer: ByteArray, offset: Int, length: Int): Int {
    if (length <= 0 || offset < 0 || offset + length > buffer.size) return 0
    if (length > 188) {
        val slice = buffer.copyOfRange(offset, offset + length)
        return anipmXorKey(slice)
    }
    val candidate = (buffer[offset].toInt() and 0xFF) xor 0x47
    return when (candidate) {
        0xFF, 0x5A, 0x00 -> candidate
        // Unknown first-byte keys wait for a longer chunk / full anipmXorKey.
        else -> 0
    }
}

/**
 * Papa / ani.pm segment unwrap — IEND cut for full PNG responses, then
 * single-byte XOR keyed so MPEG-TS sync `0x47` lands on packet boundaries.
 */
internal fun anipmXorUnwrap(raw: ByteArray): ByteArray {
    if (raw.isEmpty()) return raw
    var chunk = stripPngTrailerPayload(raw) ?: raw
    val key = anipmXorKey(chunk)
    if (key == 0) return chunk
    val out = ByteArray(chunk.size)
    for (i in chunk.indices) {
        out[i] = (chunk[i].toInt() xor key).toByte()
    }
    return out
}

/** Detect XOR key the same way animepapa's XorLoader does. */
internal fun anipmXorKey(chunk: ByteArray): Int {
    if (chunk.isEmpty()) return 0
    val candidate = (chunk[0].toInt() and 0xFF) xor 0x47
    return when {
        chunk.size > 188 &&
            (((chunk[188].toInt() and 0xFF) xor candidate) == 0x47) -> candidate
        candidate == 0xFF || candidate == 0x5A || candidate == 0x00 -> candidate
        else -> 0xFF
    }
}

/**
 * When the CDN returns a full PNG (no Range), payload sits after IEND.
 * Returns null when the buffer is not a PNG with a trailing payload.
 */
internal fun stripPngTrailerPayload(raw: ByteArray): ByteArray? {
    if (raw.size < 24) return null
    if (raw[0] != 0x89.toByte() || raw[1] != 'P'.code.toByte() ||
        raw[2] != 'N'.code.toByte() || raw[3] != 'G'.code.toByte()
    ) {
        return null
    }
    var offset = 8
    while (offset + 12 <= raw.size) {
        val size = ((raw[offset].toInt() and 0xFF) shl 24) or
            ((raw[offset + 1].toInt() and 0xFF) shl 16) or
            ((raw[offset + 2].toInt() and 0xFF) shl 8) or
            (raw[offset + 3].toInt() and 0xFF)
        if (size < 0 || size > raw.size - offset - 12) break
        val isEnd = raw[offset + 4] == 'I'.code.toByte() &&
            raw[offset + 5] == 'E'.code.toByte() &&
            raw[offset + 6] == 'N'.code.toByte() &&
            raw[offset + 7] == 'D'.code.toByte()
        offset += size + 12
        if (isEnd) {
            return if (offset < raw.size) raw.copyOfRange(offset, raw.size) else null
        }
    }
    return null
}
