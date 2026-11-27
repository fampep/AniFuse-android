// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

/** One resolver in the playback race. An inner race must finish inside this. */
internal const val RESOLVE_TIMEOUT_MS = 7_500L

/**
 * The preferred resolver starts immediately. The next ones join after
 * [headStartMs] so a dead host does not hold the episode for its full timeout.
 * A null result is a miss. The first non-null value wins and the rest stop.
 * Indexes past [parallel] run only after that first window, so a caller inside
 * [RESOLVE_TIMEOUT_MS] must set [parallel] to [count] or the rest never start.
 */
internal suspend fun <T> raceHeadStart(
    count: Int,
    parallel: Int = 3,
    headStartMs: Long = 1_500L,
    timeoutMs: Long = 8_000L,
    block: suspend (index: Int) -> T?,
): Pair<Int, T>? = coroutineScope {
    if (count <= 0) return@coroutineScope null
    val window = parallel.coerceAtLeast(1).coerceAtMost(count)
    val winner = CompletableDeferred<Pair<Int, T>>()
    val jobs = (0 until window).map { index ->
        launch {
            if (index > 0 && headStartMs > 0) delay(headStartMs)
            if (winner.isCompleted) return@launch
            val value = try {
                withTimeoutOrNull(timeoutMs) { block(index) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (value != null && !winner.isCompleted) winner.complete(index to value)
        }
    }
    yield()
    val raced = withTimeoutOrNull(headStartMs + timeoutMs + 250L) {
        // Wait until someone wins, but do not hang if every racer misses.
        while (!winner.isCompleted && jobs.any { it.isActive }) {
            delay(20)
        }
        if (winner.isCompleted) winner.await() else null
    }
    jobs.forEach { it.cancel() }
    if (raced != null) return@coroutineScope raced
    for (index in window until count) {
        val value = try {
            withTimeoutOrNull(timeoutMs) { block(index) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (value != null) return@coroutineScope index to value
    }
    null
}
