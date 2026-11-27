// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.anifuse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.anikitty.app.data.servers.resolveHeadStartMs
import site.anikitty.app.data.servers.sinkUnhealthy
import java.io.File

class AnifuseFeedCacheTest {
    @Test
    fun aFeedSurvivesUntilTheStaleWindowAndThenExpires() {
        val dir = File.createTempFile("feeds", "").apply {
            delete()
            mkdirs()
        }
        var now = 1_000L
        val cache = AnifuseFeedCache(dir) { now }
        cache.write("latest.json", "{\"ok\":true}")
        assertEquals("{\"ok\":true}", cache.read("latest.json", AnifuseFeedCache.STALE_FEED_MS))
        now += AnifuseFeedCache.STALE_FEED_MS + 1
        assertNull(cache.read("latest.json", AnifuseFeedCache.STALE_FEED_MS))
        dir.deleteRecursively()
    }

    @Test
    fun downNamesAreLowercasedAndIgnoredOnceTheyExpire() {
        val dir = File.createTempFile("health", "").apply {
            delete()
            mkdirs()
        }
        var now = 5_000L
        val cache = AnifuseFeedCache(dir) { now }
        cache.writeDown(listOf("AniHosts", "Gogo"))
        assertEquals(setOf("anihosts", "gogo"), cache.downNames())
        now += AnifuseFeedCache.DOWN_MAX_AGE_MS + 5
        assertTrue(cache.downNames().isEmpty())
        dir.deleteRecursively()
    }

    @Test
    fun theSelectedResolverStaysFirstAndDownOnesSink() {
        val ordered = listOf("anifuse", "gogo", "anihosts", "anizone")
        val next = sinkUnhealthy(
            ordered = ordered,
            down = setOf("gogo"),
            id = { it },
            name = { it },
        )
        assertEquals(listOf("anifuse", "anihosts", "anizone", "gogo"), next)
        val selectedDown = sinkUnhealthy(
            ordered = listOf("gogo", "anizone"),
            down = setOf("gogo"),
            id = { it },
            name = { it },
        )
        assertEquals(listOf("gogo", "anizone"), selectedDown)
    }

    @Test
    fun aLocalFailureHoldsForThreeMinutesAndASuccessClearsIt() {
        val dir = File.createTempFile("pulse", "").apply {
            delete()
            mkdirs()
        }
        var now = 10_000L
        val cache = AnifuseFeedCache(dir) { now }
        cache.notePulse(listOf("AniHosts"), ok = false)
        assertEquals(setOf("anihosts"), cache.pulseDownKeys())
        cache.notePulse(listOf("AniHosts"), ok = true)
        assertTrue(cache.pulseDownKeys().isEmpty())
        cache.notePulse(listOf("gogo"), ok = false)
        now += AnifuseFeedCache.LOCAL_HOLD_MS + 5
        assertTrue(cache.pulseDownKeys().isEmpty())
        dir.deleteRecursively()
    }

    @Test
    fun aColdPreferredResolverLosesItsHeadStart() {
        assertEquals(900L, resolveHeadStartMs(anyDown = false, preferredCold = false))
        assertEquals(350L, resolveHeadStartMs(anyDown = true, preferredCold = false))
        assertEquals(0L, resolveHeadStartMs(anyDown = true, preferredCold = true))
    }
}
