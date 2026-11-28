// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

/**
 * Sub / Dub chip id from the episode audio mode string.
 * Anything other than explicit "dub" is treated as Sub.
 */
internal fun glassAudioModeSelectedId(audio: String): String =
    if (audio.trim().equals("dub", ignoreCase = true)) "dub" else "sub"

/** Compact chip label for Sub / Dub (and longer Audio page labels). */
internal fun glassAudioModeChipLabel(raw: String): String {
    val trimmed = raw.trim()
    return when {
        trimmed.equals("dub", ignoreCase = true) ||
            trimmed.equals("dubbed", ignoreCase = true) -> "Dub"
        trimmed.equals("sub", ignoreCase = true) ||
            trimmed.equals("subtitled", ignoreCase = true) -> "Sub"
        else -> trimmed.take(18).ifEmpty { "Audio" }
    }
}

/**
 * Which caption chip is selected given the live Subs toggles.
 * Off wins when captions are disabled even if a language id is still held.
 */
internal fun glassSubsSelectedCaptionId(subsEnabled: Boolean, selectedTextId: String): String =
    when {
        !subsEnabled || selectedTextId == "off" -> "off"
        selectedTextId.isBlank() || selectedTextId == "auto" -> "auto"
        else -> selectedTextId
    }

/** Compact caption chip label — Off / Auto / language. */
internal fun glassCaptionChipLabel(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return "Captions"
    if (trimmed.equals("off", ignoreCase = true)) return "Off"
    if (trimmed.equals("auto", ignoreCase = true)) return "Auto"
    return trimmed.take(22)
}

/** TalkBack string for a caption / audio mode chip. */
internal fun glassSubsChipDescription(label: String, selected: Boolean): String =
    if (selected) "$label, selected" else label

/**
 * Build Off · Auto · language options for the glass caption row.
 * [languages] are (id, label) pairs excluding off/auto.
 */
internal fun glassCaptionChipOptions(
    languages: List<Pair<String, String>>,
): List<Pair<String, String>> = buildList {
    add("off" to "Off")
    add("auto" to "Auto")
    languages.forEach { (id, label) ->
        if (id != "off" && id != "auto") add(id to glassCaptionChipLabel(label))
    }
}

/** Apply a caption chip tap onto (enabled, selectedId). */
internal fun glassCaptionSelectionAfterTap(
    id: String,
): Pair<Boolean, String> = when (id) {
    "off" -> false to "off"
    "auto" -> true to "auto"
    else -> true to id
}

/**
 * Placeholder chips while the library fetch is in flight — glass plane, not
 * a bare gray "Loading subtitles…" string.
 */
internal fun glassCaptionsLoadingChipOptions(): List<Pair<String, String>> = listOf(
    "cc-warm" to "CC · WARM",
    "cc-slot-a" to "····",
    "cc-slot-b" to "····",
)

internal fun glassCaptionsLoadingLabel(): String = "Warming captions"
