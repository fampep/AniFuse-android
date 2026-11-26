// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.anifuse

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnifuseApiTest {

    @Test
    fun normalizeKeepsHttpsAndStripsTrailingSlashes() {
        assertEquals(
            "https://143-246-203-212.sslip.io",
            AnifuseApiStore.normalize("https://143-246-203-212.sslip.io/"),
        )
        assertEquals(
            "https://143-246-203-212.sslip.io",
            AnifuseApiStore.normalize("   https://143-246-203-212.sslip.io//  "),
        )
    }

    @Test
    fun normalizeAddsHttpsScheme() {
        assertEquals(
            "https://anifuse.example.com",
            AnifuseApiStore.normalize("anifuse.example.com/"),
        )
    }

    @Test
    fun normalizeKeepsExplicitHttpForLocalTesting() {
        assertEquals(
            "http://192.168.1.10:8080",
            AnifuseApiStore.normalize("http://192.168.1.10:8080/"),
        )
    }

    @Test
    fun normalizeBlankFallsBackToDemo() {
        assertEquals(AnifuseApiStore.DEFAULT_BASE_URL, AnifuseApiStore.normalize(""))
        assertEquals(AnifuseApiStore.DEFAULT_BASE_URL, AnifuseApiStore.normalize("   "))
        assertEquals(AnifuseApiStore.DEFAULT_BASE_URL, AnifuseApiStore.normalize(null))
    }

    @Test
    fun healthParsesAllFields() {
        val health = healthFromJson(
            JSONObject(
                """{"status":"ok","providers":12,"uptimeSeconds":47948,"demo":true,"unhealthy":0}""",
            ),
        )
        assertTrue(health.ok)
        assertEquals(12, health.providers)
        assertEquals(47948L, health.uptimeSeconds)
        assertTrue(health.demo)
        assertEquals(0, health.unhealthy)
    }

    @Test
    fun healthToleratesMissingFields() {
        val health = healthFromJson(JSONObject("""{"status":"ok"}"""))
        assertTrue(health.ok)
        assertEquals(0, health.providers)
        assertEquals(0L, health.uptimeSeconds)
        assertFalse(health.demo)
    }

    @Test
    fun providerParsesAndComputesRatePercent() {
        val provider = providerFromJson(
            JSONObject(
                """
                {"id":"anihosts","name":"AniHosts","siteUrl":"https://anihosts.xyz",
                 "bundled":true,"status":"ok","successRate":0.987,"attempts":87,"cooldownSeconds":0}
                """.trimIndent(),
            ),
        )
        assertEquals("anihosts", provider.id)
        assertTrue(provider.healthy)
        assertEquals(98, provider.ratePercent)
        assertEquals(87, provider.attempts)
    }

    @Test
    fun providerStatusFlags() {
        val degraded = providerFromJson(
            JSONObject("""{"id":"x","name":"X","status":"degraded","successRate":0.5,"attempts":2}"""),
        )
        assertTrue(degraded.degraded)
        assertFalse(degraded.healthy)
        val down = providerFromJson(
            JSONObject("""{"id":"y","name":"Y","status":"down","successRate":0,"attempts":4}"""),
        )
        assertTrue(down.down)
    }

    @Test
    fun providersSortHealthyFirstThenSuccessRate() {
        val root = JSONObject(
            """
            {"providers":[
              {"id":"a","name":"A","status":"down","successRate":0.1,"attempts":1},
              {"id":"b","name":"B","status":"ok","successRate":0.8,"attempts":2},
              {"id":"c","name":"C","status":"ok","successRate":1.0,"attempts":3},
              {"id":"d","name":"D","status":"degraded","successRate":0.9,"attempts":4}
            ]}
            """.trimIndent(),
        )
        val sorted = providersFromJson(root).map { it.id }
        assertEquals(listOf("c", "b", "d", "a"), sorted)
    }

    @Test
    fun latestParsesRowsAndToleratesMissingOptionals() {
        val root = JSONObject(
            """
            {"latest":[
              {"provider":"anikoto","title":"One Piece","episode":1180,
               "imageUrl":"https://img.example/one.jpg","siteUrl":"https://anikoto.cz/x"},
              {"provider":"anihosts","title":"Renegade Immortal","episode":168}
            ]}
            """.trimIndent(),
        )
        val items = latestFromJson(root)
        assertEquals(2, items.size)
        assertEquals("One Piece", items[0].title)
        assertEquals(1180, items[0].episode)
        assertEquals("anikoto", items[0].provider)
        assertTrue(items[0].siteUrl != null)
        assertEquals(null, items[1].imageUrl)
        assertEquals(null, items[1].updatedAt)
    }

    @Test
    fun latestRejectsRowsWithoutEpisode() {
        val root = JSONObject("""{"latest":[{"provider":"x","title":"No episode"}]}""")
        val items = latestFromJson(root)
        assertEquals(1, items.size)
        assertEquals(0, items[0].episode)
    }
}
