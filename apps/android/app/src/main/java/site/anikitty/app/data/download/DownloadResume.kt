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
