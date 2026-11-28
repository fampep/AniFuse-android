// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

/** My Space / Settings CloudSync subtitle while AniList drain + import run. */
internal fun cloudSyncStatusLabel(syncing: Boolean): String =
    if (syncing) "Pushing & importing…" else "Connected"
