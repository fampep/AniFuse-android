// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import site.anikitty.app.data.anilist.AniListSyncPhase
import site.anikitty.app.data.anilist.aniListPendingPushPill

/** My Space / Settings CloudSync subtitle while AniList drain + import run. */
internal fun cloudSyncStatusLabel(syncing: Boolean): String =
    if (syncing) "Pushing & importing…" else "Connected"

/** Phase-aware glass subtitle — Sync no longer looks like one frozen spinner. */
internal fun cloudSyncStatusLabel(syncLabel: String?): String = when {
    syncLabel.isNullOrBlank() -> "Connected"
    syncLabel.startsWith("Pushing") -> syncLabel
    syncLabel.startsWith("Importing") -> syncLabel
    syncLabel.startsWith("SYNC ·") -> syncLabel
    syncLabel == "Syncing…" -> "Pushing & importing…"
    else -> "Connected"
}

/**
 * Prefer an explicit Sync phase; otherwise surface offline-first queued marks
 * as `SYNC · N` so the glass dock shows the drain is still owed.
 */
internal fun cloudSyncStatusLabel(syncLabel: String?, pendingCount: Int): String {
    val phase = cloudSyncStatusLabel(syncLabel)
    if (phase != "Connected") return phase
    return aniListPendingPushPill(pendingCount) ?: "Connected"
}

internal fun cloudSyncIsBusy(syncLabel: String?): Boolean =
    syncLabel == "Syncing…" ||
        syncLabel?.startsWith("Pushing") == true ||
        syncLabel?.startsWith("Importing") == true ||
        syncLabel?.startsWith("SYNC ·") == true

internal fun cloudSyncPhaseLabel(phase: AniListSyncPhase): String = when (phase) {
    AniListSyncPhase.Pushing -> "Pushing marks…"
    AniListSyncPhase.Importing -> "Importing list…"
}
