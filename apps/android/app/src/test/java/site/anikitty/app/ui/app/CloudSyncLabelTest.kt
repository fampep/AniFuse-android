// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import site.anikitty.app.data.anilist.AniListSyncPhase

class CloudSyncLabelTest {
    @Test
    fun syncingShowsDrainAwareSubtitle() {
        assertEquals("Connected", cloudSyncStatusLabel(syncing = false))
        assertEquals("Pushing & importing…", cloudSyncStatusLabel(syncing = true))
    }

    @Test
    fun phaseLabelsDriveGlassSubtitle() {
        assertEquals("Connected", cloudSyncStatusLabel(null))
        assertEquals("Pushing marks…", cloudSyncStatusLabel(cloudSyncPhaseLabel(AniListSyncPhase.Pushing)))
        assertEquals("Importing list…", cloudSyncStatusLabel(cloudSyncPhaseLabel(AniListSyncPhase.Importing)))
        assertEquals("Pushing & importing…", cloudSyncStatusLabel("Syncing…"))
        assertEquals("Connected", cloudSyncStatusLabel("Synced 12 from AniList"))
        assertTrue(cloudSyncIsBusy("Pushing marks…"))
        assertTrue(cloudSyncIsBusy("Importing list…"))
        assertFalse(cloudSyncIsBusy("Synced 12 from AniList"))
        assertFalse(cloudSyncIsBusy(null))
    }
}
