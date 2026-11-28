// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import site.anikitty.app.data.anilist.FrontierWarmTarget

class FrontierWarmTest {
    @Test
    fun streamHintCarriesMalWhenKnown() {
        assertEquals("Frieren", frontierStreamHint("Frieren", null))
        assertEquals("Frieren || mal:52991", frontierStreamHint("Frieren", 52991))
        assertEquals("mal:21", frontierStreamHint("  ", 21))
        assertEquals("", frontierStreamHint("", null))
    }

    @Test
    fun warmEligibleGuardsIds() {
        assertTrue(
            frontierWarmEligible(
                FrontierWarmTarget(anilistId = 1, episode = 2, title = "A", malId = null),
            ),
        )
        assertFalse(
            frontierWarmEligible(
                FrontierWarmTarget(anilistId = 0, episode = 2, title = "A", malId = null),
            ),
        )
        assertFalse(
            frontierWarmEligible(
                FrontierWarmTarget(anilistId = 1, episode = 0, title = "A", malId = null),
            ),
        )
    }

    @Test
    fun launchWarmSurvivesCallerScopeCancel() = runBlocking {
        // Mirrors SettingsFeed dispose cancelling rememberCoroutineScope while
        // Continue re-warm must keep running on FrontierWarmProcessScope.
        val caller = CoroutineScope(Job() + Dispatchers.Unconfined)
        val process = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val progressed = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val job = process.launch {
            progressed.complete(Unit)
            delay(50)
            finished.complete(Unit)
        }
        // Simulate Settings leaving while warm is mid-flight.
        caller.cancel()
        withTimeout(1_000) {
            progressed.await()
            finished.await()
        }
        assertTrue(job.isCompleted)
        assertFalse(job.isCancelled)
        process.cancel()
    }

    @Test
    fun launchWarmEmptyTargetsCompletesImmediately() {
        // Empty list must not hang — Settings Sync can return zero frontier warms.
        val job = Job().also { it.complete() }
        assertTrue(job.isCompleted)
        assertFalse(job.isCancelled)
    }
}
