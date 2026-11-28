// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import site.anikitty.app.data.anilist.AniListSyncPhase

/** My Space / Settings CloudSync subtitle while AniList drain + import run. */
internal fun cloudSyncStatusLabel(syncing: Boolean): String =
    if (syncing) "Pushing & importing…" else "Connected"

/** Phase-aware glass subtitle — Sync no longer looks like one frozen spinner. */
internal fun cloudSyncStatusLabel(syncLabel: String?): String = when {
    syncLabel.isNullOrBlank() -> "Connected"
    syncLabel.startsWith("Pushing") -> syncLabel
    syncLabel.startsWith("Importing") -> syncLabel
    syncLabel == "Syncing…" -> "Pushing & importing…"
    else -> "Connected"
}

internal fun cloudSyncIsBusy(syncLabel: String?): Boolean =
    syncLabel == "Syncing…" ||
        syncLabel?.startsWith("Pushing") == true ||
        syncLabel?.startsWith("Importing") == true

internal fun cloudSyncPhaseLabel(phase: AniListSyncPhase): String = when (phase) {
    AniListSyncPhase.Pushing -> "Pushing marks…"
    AniListSyncPhase.Importing -> "Importing list…"
}
