// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.anifuse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import site.anikitty.app.data.anilist.AnimeSummary
import site.anikitty.app.data.servers.ProviderLatestMapper
import java.io.File

class LatestResolveTest {
    @Test
    fun duplicateTitlesCollapseAndAMissIsDropped() {
        val frieren = summary(1, "Frieren")
        val slots = AnifuseLatestMapper.slotsFromResolved(
            items = listOf(
                AnifuseLatestItem("a", "Frieren", 12, "https://img", null, null),
                AnifuseLatestItem("b", "Frieren", 12, null, null, null),
                AnifuseLatestItem("c", "Unknown Show", 1, null, null, null),
            ),
            resolved = mapOf("Frieren" to frieren),
        )
        assertEquals(1, slots.size)
        assertEquals(1, slots[0].anime.id)
        assertEquals(12, slots[0].episode)
        assertEquals("https://img", slots[0].anime.coverImageUrl)
    }

    @Test
    fun aStoredTitleIsNotExpiredInsideFourteenDays() {
        val file = File.createTempFile("titles", ".json")
        file.deleteOnExit()
        val store = TitleResolveStore(file, now = { 1_000L }, flushDelayMs = 60_000L)
        store.put(ProviderLatestMapper.normalize("Frieren"), summary(154587, "Frieren"))
        store.flushNow()
        val again = TitleResolveStore(file, now = { 1_000L + 1_000L }, flushDelayMs = 60_000L)
        assertEquals(154587, again.get(ProviderLatestMapper.normalize("Frieren"))?.id)
        val later = TitleResolveStore(file, now = { 1_000L + 15L * 24 * 60 * 60 * 1000 })
        assertNull(later.get(ProviderLatestMapper.normalize("Frieren")))
    }

    private fun summary(id: Int, title: String) = AnimeSummary(
        id = id,
        title = title,
        coverImageUrl = null,
        episodes = 28,
        format = "TV",
        averageScore = 80,
    )
}
