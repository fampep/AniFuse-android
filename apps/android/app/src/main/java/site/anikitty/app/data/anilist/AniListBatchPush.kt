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
internal const val ANILIST_PUSH_CHUNK = 14

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
 * Freshest binge marks go first so a long offline queue does not stall the
 * episode the person just finished behind dozens of older titles.
 */
internal fun <T> pendingPushesForDrain(
    rows: List<T>,
    updatedAtOf: (T) -> Long,
): List<T> = rows.sortedByDescending(updatedAtOf)

/**
 * Rows a multi-alias batch left behind. When the batch was a partial success,
 * these retry as singles in the same drain so one bad mediaId does not wait
 * for the next 5-minute sync loop.
 */
internal fun <T> pendingPushRejected(
    chunk: List<T>,
    acceptedIds: Set<Int>,
    idOf: (T) -> Int,
): List<T> = chunk.filter { idOf(it) !in acceptedIds }

/** True when some — but not all — aliases landed; triggers same-drain singles. */
internal fun pendingPushPartialSuccess(chunkSize: Int, acceptedCount: Int): Boolean =
    chunkSize > 0 && acceptedCount > 0 && acceptedCount < chunkSize

/** True when GraphQL returned a data object even if errors[] is present. */
internal fun aniListBodyHasData(body: String): Boolean =
    runCatching { org.json.JSONObject(body).optJSONObject("data") != null }.getOrDefault(false)

/** Map alias-index successes → mediaIds. */
internal fun acceptedMediaIdsFromBatch(
    body: String,
    items: List<AniListBatchPushItem>,
): Set<Int> {
    val ok = parseSaveProgressBatchSuccess(body, expected = items.size)
    return ok.mapNotNullTo(LinkedHashSet()) { items.getOrNull(it)?.mediaId }
}

/**
 * Cap same-drain singles so a poisoned chunk cannot add N×700ms to Sync.
 * Re-batch leftovers first; singles only for stragglers.
 */
internal data class PendingPushRetryPlan(
    val rebatch: List<AniListBatchPushItem>,
    val singles: List<AniListBatchPushItem>,
    val deferred: List<AniListBatchPushItem>,
)

internal fun pendingPushRetryPlan(
    rejected: List<AniListBatchPushItem>,
    maxSingles: Int = 3,
    rebatchSize: Int = 4,
): PendingPushRetryPlan {
    if (rejected.isEmpty()) {
        return PendingPushRetryPlan(emptyList(), emptyList(), emptyList())
    }
    val rebatchCap = rebatchSize.coerceAtLeast(0)
    val singleCap = maxSingles.coerceAtLeast(0)
    val rebatch = rejected.take(rebatchCap)
    val afterRebatch = rejected.drop(rebatchCap)
    val singles = afterRebatch.take(singleCap)
    val deferred = afterRebatch.drop(singleCap)
    return PendingPushRetryPlan(rebatch = rebatch, singles = singles, deferred = deferred)
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
