// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Plain SRT files served by the subtitle library. The app does not scrape these. */
internal const val SUBTITLE_LIBRARY_ORIGIN = "http://143.246.203.212"

/** A listing stays warm for half an hour; the player usually opens far sooner. */
internal const val CAPTION_TTL_MS = 30 * 60 * 1000L

/** "No captions here" is remembered briefly so a dead library is not re-asked on every open. */
internal const val CAPTION_MISS_TTL_MS = 2 * 60 * 1000L

private val LIBRARY_FILE = Regex("^/api/file\\?id=([a-f0-9]{64})$")

private val ISO_LANGUAGE = mapOf(
    "english" to "en",
    "japanese" to "ja",
    "spanish" to "es",
    "french" to "fr",
    "german" to "de",
    "portuguese" to "pt",
    "italian" to "it",
    "russian" to "ru",
    "arabic" to "ar",
    "chinese" to "zh",
    "korean" to "ko",
    "hindi" to "hi",
    "dutch" to "nl",
    "polish" to "pl",
    "turkish" to "tr",
    "vietnamese" to "vi",
    "thai" to "th",
    "romanian" to "ro",
    "czech" to "cs",
    "ukrainian" to "uk",
    "albanian" to "sq",
    "swedish" to "sv",
    "danish" to "da",
    "norwegian" to "no",
    "finnish" to "fi",
    "greek" to "el",
    "hebrew" to "he",
    "indonesian" to "id",
    "malay" to "ms",
    "hungarian" to "hu",
    "bulgarian" to "bg",
    "croatian" to "hr",
    "serbian" to "sr",
    "slovak" to "sk",
)

internal fun captionKey(anilistId: Int, episode: Int): String = "$anilistId/$episode"

/**
 * Thread-safe, TTL-bounded memory for caption listings. A success stays warm
 * for [ttlMs]; an "empty" answer (no captions) is remembered only for [missTtlMs]
 * so a temporarily broken library recovers quickly.
 */
internal class CaptionMemoryCache(
    private val ttlMs: Long = CAPTION_TTL_MS,
    private val missTtlMs: Long = CAPTION_MISS_TTL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val tracks: List<SubtitleTrack>, val atMs: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    fun get(key: String): List<SubtitleTrack>? {
        val entry = entries[key] ?: return null
        val age = clock() - entry.atMs
        val ttl = if (entry.tracks.isEmpty()) missTtlMs else ttlMs
        if (age <= ttl) return entry.tracks
        entries.remove(key)
        return null
    }

    fun put(key: String, tracks: List<SubtitleTrack>) {
        entries[key] = Entry(tracks, clock())
    }

    fun clear() = entries.clear()
}

/** A stored listing body plus the moment the app saved it. */
internal data class StoredCaptionBody(val body: String, val savedAtMs: Long)

internal interface CaptionBodyStore {
    fun read(key: String): StoredCaptionBody?
    fun write(key: String, body: String)
}

/**
 * Best-effort copy of the listing under the app cache dir. A re-opened episode
 * then shows its subtitle menu before (or without) the network answering.
 */
internal class CaptionFileStore(private val dir: File) : CaptionBodyStore {
    override fun read(key: String): StoredCaptionBody? {
        return runCatching {
            val file = fileFor(key)
            if (!file.isFile) return@runCatching null
            val body = file.readText()
            if (body.isBlank()) null else StoredCaptionBody(body, file.lastModified())
        }.getOrNull()
    }

    override fun write(key: String, body: String) {
        runCatching {
            dir.mkdirs()
            val target = fileFor(key)
            val tmp = File(dir, "${target.name}.tmp")
            tmp.writeText(body)
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        }
    }

    private fun fileFor(key: String) = File(dir, key.replace('/', '_') + ".json")
}

internal object SubtitleLibrary {
    private const val TAG = "AniKittySubs"

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val cache = CaptionMemoryCache()
    private val inflight = HashMap<String, Deferred<List<SubtitleTrack>>>()
    private val inflightLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var store: CaptionBodyStore? = null

    /** Called once from the Application. The object still works without it. */
    fun install(context: Context) {
        store = CaptionFileStore(File(context.applicationContext.cacheDir, "captions"))
    }

    /** Captions already known, without touching the network. Safe on any thread. */
    fun peek(anilistId: Int, episode: Int): List<SubtitleTrack>? {
        if (anilistId <= 0 || episode <= 0) return null
        return cache.get(captionKey(anilistId, episode))
    }

    /** Fetch now and keep the result warm for the player that is about to open. */
    suspend fun prefetch(anilistId: Int, episode: Int) {
        if (anilistId <= 0 || episode <= 0) return
        try {
            tracks(anilistId, episode)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Caption prefetch failed for $anilistId/$episode", error)
        }
    }

    suspend fun tracks(anilistId: Int, episode: Int): List<SubtitleTrack> {
        if (anilistId <= 0 || episode <= 0) return emptyList()
        val key = captionKey(anilistId, episode)
        cache.get(key)?.let { return it }
        // One request per episode even when the player, the cast relay, and a
        // prefetch all ask at once.
        val work = inflightLock.withLock {
            inflight[key] ?: scope.async { load(key, anilistId, episode) }.also { inflight[key] = it }
        }
        return try {
            work.await()
        } finally {
            withContext(NonCancellable) {
                inflightLock.withLock {
                    if (inflight[key] === work) inflight.remove(key)
                }
            }
        }
    }

    private suspend fun load(key: String, anilistId: Int, episode: Int): List<SubtitleTrack> {
        val stored = store?.read(key)
        if (stored != null && System.currentTimeMillis() - stored.savedAtMs <= CAPTION_TTL_MS) {
            val fresh = withContext(Dispatchers.Default) { subtitleTracksFromLibrary(stored.body) }
            if (fresh.isNotEmpty()) {
                cache.put(key, fresh)
                return fresh
            }
        }
        val body = try {
            fetchBody(anilistId, episode)
        } catch (error: Exception) {
            // The library is unreachable. A copy from an earlier visit still
            // gives the viewer a subtitle menu instead of none.
            val fallback = stored?.let { subtitleTracksFromLibrary(it.body) }.orEmpty()
            if (fallback.isNotEmpty()) {
                cache.put(key, fallback)
                return fallback
            }
            throw error
        }
        val parsed = withContext(Dispatchers.Default) {
            if (body.isEmpty()) emptyList() else subtitleTracksFromLibrary(body)
        }
        cache.put(key, parsed)
        if (body.isNotEmpty() && parsed.isNotEmpty()) store?.write(key, body)
        return parsed
    }

    private fun fetchBody(anilistId: Int, episode: Int): String {
        val request = Request.Builder()
            .url("$SUBTITLE_LIBRARY_ORIGIN/api/subs/$anilistId/$episode")
            .header("Accept", "application/json")
            .header("User-Agent", "AniFuse-Captions/1")
            .build()
        // A dropped connection is worth one more try. A real HTTP answer is not.
        var last: Exception? = null
        repeat(2) { attempt ->
            try {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return ""
                    return response.body?.string().orEmpty()
                }
            } catch (error: Exception) {
                last = error
                Log.w(TAG, "Caption listing failed for $anilistId/$episode (try ${attempt + 1})", error)
            }
        }
        throw last ?: IOException("Caption listing failed")
    }
}

/**
 * Captions for a player that has already started. A cancelled load aborts.
 * Any other failure leaves playback with no sidecar files.
 */
internal suspend fun libraryCaptionsOrEmpty(anilistId: Int, episode: Int): List<SubtitleTrack> {
    val loaded = try {
        SubtitleLibrary.tracks(anilistId, episode)
    } catch (error: Exception) {
        valueOrEmptyUnlessCancelled(Result.failure(error))
    }
    return loaded ?: emptyList()
}

/** Same policy as [libraryCaptionsOrEmpty], for callers that already have the result. */
internal fun <T> valueOrEmptyUnlessCancelled(result: Result<T>): T? {
    val error = result.exceptionOrNull() ?: return result.getOrNull()
    if (error is CancellationException) throw error
    return null
}

/** Accept only library file ids. A track that points anywhere else is dropped. */
internal fun subtitleTracksFromLibrary(
    body: String,
    origin: String = SUBTITLE_LIBRARY_ORIGIN,
): List<SubtitleTrack> {
    val rows = runCatching { JSONObject(body).optJSONArray("tracks") }.getOrNull() ?: return emptyList()
    val parsed = ArrayList<Pair<SubtitleTrack, String>>()
    for (index in 0 until rows.length()) {
        val row = rows.optJSONObject(index) ?: continue
        val fileId = LIBRARY_FILE.matchEntire(row.optString("download").trim())?.groupValues?.get(1)
            ?: continue
        val language = row.optString("language").trim().ifBlank { "Unknown" }
        val flag = row.optString("flag").trim()
        val site = row.optString("site").trim()
        val sdh = row.optBoolean("sdh")
        val label = buildString {
            if (flag.isNotEmpty()) {
                append(flag)
                append(' ')
            }
            append(language)
            if (sdh) append(" SDH")
        }
        parsed += SubtitleTrack(
            url = "$origin/api/file?id=$fileId",
            label = label,
            language = ISO_LANGUAGE[language.lowercase()] ?: "und",
            mimeType = "application/x-subrip",
        ) to site
    }
    val counts = parsed.groupingBy { it.first.label }.eachCount()
    return parsed.map { (track, site) ->
        if (site.isNotEmpty() && (counts[track.label] ?: 0) > 1) {
            track.copy(label = "${track.label} · $site")
        } else {
            track
        }
    }
}
