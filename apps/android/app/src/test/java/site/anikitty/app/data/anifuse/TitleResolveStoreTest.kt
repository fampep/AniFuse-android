// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.anifuse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import site.anikitty.app.data.anilist.AnimeSummary

class TitleResolveStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun putIsReadableBeforeAndAfterFlush() {
        val file = tmp.newFile("titles.json")
        val store = TitleResolveStore(file, flushDelayMs = 60_000L)
        store.put("one piece", AnimeSummary(id = 21, title = "One Piece"))
        assertEquals(21, store.get("one piece")?.id)
        store.flushNow()
        val reloaded = TitleResolveStore(file, flushDelayMs = 60_000L)
        assertEquals(21, reloaded.get("one piece")?.id)
        assertNull(reloaded.get("missing"))
    }
}
