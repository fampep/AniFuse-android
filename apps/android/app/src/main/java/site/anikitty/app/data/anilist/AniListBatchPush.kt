// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.anilist

/**
 * One SaveMediaListEntry slot inside a multi-alias mutation.
 * Aliases keep a single paced HTTP round-trip for a chunk of pending pushes.
 */
internal data class AniListBatchPushItem(
    val mediaId: Int,
    val progress: Int,
    val status: String,
)

/** Chunk size that stays under AniList's complexity budget while cutting ~700ms×N waits. */
/** Alias batch size — one paced HTTP call covers this many progress rows. */
internal const val ANILIST_PUSH_CHUNK = 10

internal fun pendingPushChunks(
    count: Int,
    chunkSize: Int = ANILIST_PUSH_CHUNK,
): List<IntRange> {
    if (count <= 0) return emptyList()
    val size = chunkSize.coerceAtLeast(1)
    return (0 until count step size).map { start ->
        start until minOf(count, start + size)
    }
}

/**
 * Builds a multi-alias SaveMediaListEntry mutation.
 * Status is emitted as a GraphQL enum token (CURRENT / COMPLETED).
 */
internal fun buildSaveProgressBatchMutation(items: List<AniListBatchPushItem>): String {
    require(items.isNotEmpty())
    val body = buildString {
        items.forEachIndexed { index, item ->
            val status = item.status.trim().uppercase().ifBlank { "CURRENT" }
            append("e")
            append(index)
            append(": SaveMediaListEntry(mediaId: ")
            append(item.mediaId)
            append(", status: ")
            append(status)
            append(", progress: ")
            append(item.progress.coerceAtLeast(0))
            append(") { id }\n")
        }
    }
    return "mutation {\n$body}"
}

/** Alias indices whose SaveMediaListEntry node came back non-null. */
internal fun parseSaveProgressBatchSuccess(
    body: String,
    expected: Int,
): Set<Int> {
    if (expected <= 0) return emptySet()
    val data = runCatching {
        org.json.JSONObject(body).optJSONObject("data")
    }.getOrNull() ?: return emptySet()
    val ok = LinkedHashSet<Int>()
    for (index in 0 until expected) {
        if (data.optJSONObject("e$index") != null) ok += index
    }
    return ok
}
