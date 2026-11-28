// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudSyncLabelTest {
    @Test
    fun syncingShowsDrainAwareSubtitle() {
        assertEquals("Connected", cloudSyncStatusLabel(syncing = false))
        assertEquals("Pushing & importing…", cloudSyncStatusLabel(syncing = true))
    }
}
