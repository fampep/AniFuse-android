// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.download

/**
 * A dropped progressive save keeps the partial file and asks the CDN for the rest.
 * [append] is true only when the server answers 206. A 200 means the body is the
 * whole file and the partial must be replaced.
 */
internal data class ProgressiveResume(
    val rangeHeader: String?,
    val appendOnPartial: Boolean,
)

internal fun progressiveResume(partBytes: Long): ProgressiveResume {
    if (partBytes <= 0L) return ProgressiveResume(rangeHeader = null, appendOnPartial = false)
    return ProgressiveResume(rangeHeader = "bytes=$partBytes-", appendOnPartial = true)
}

/**
 * Parse the start offset from a Content-Range header.
 * Accepts `bytes 100-999/1234` and `bytes 100-/1234`.
 */
internal fun contentRangeStart(contentRange: String?): Long? {
    val raw = contentRange?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val match = Regex("""(?i)bytes\s+(\d+)-""").find(raw) ?: return null
    return match.groupValues[1].toLongOrNull()
}

/** True when a 206 body continues exactly where the partial left off. */
internal fun contentRangeMatches(partBytes: Long, contentRange: String?): Boolean {
    if (partBytes <= 0L) return false
    val start = contentRangeStart(contentRange) ?: return false
    return start == partBytes
}

/**
 * A 206 without a matching Content-Range is corrupt — restart the partial.
 * Other codes keep [downloadTransfer] behavior.
 */
internal fun downloadTransferValidated(
    code: Int,
    partBytes: Long,
    contentRange: String?,
): DownloadTransfer {
    val base = downloadTransfer(code, partBytes)
    if (base != DownloadTransfer.Resume) return base
    return if (contentRangeMatches(partBytes, contentRange)) {
        DownloadTransfer.Resume
    } else {
        DownloadTransfer.Restart
    }
}
