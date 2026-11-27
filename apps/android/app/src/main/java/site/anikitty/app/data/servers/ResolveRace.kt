// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicInteger

/** One resolver in the playback race. An inner race must finish inside this. */
internal const val RESOLVE_TIMEOUT_MS = 7_500L

/**
 * The preferred resolver starts immediately. The next ones in the first window
 * join after [headStartMs] so a dead host does not hold the episode for its
 * full timeout. When a racer misses, the next unused index starts immediately —
 * a sliding window, not a serial tail — so provider #5+ still race in parallel.
 * A null result is a miss. The first non-null value wins and losing racers are
 * cancelled so playback is not held for their timeouts.
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
    val nextTail = AtomicInteger(window)
    val active = AtomicInteger(0)

    fun launchRacer(index: Int, initialDelayMs: Long) {
        active.incrementAndGet()
        launch {
            try {
                if (initialDelayMs > 0L) delay(initialDelayMs)
                if (winner.isCompleted) return@launch
                val value = try {
                    withTimeoutOrNull(timeoutMs) { block(index) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                if (value != null && !winner.isCompleted) {
                    winner.complete(index to value)
                    return@launch
                }
                if (!winner.isCompleted) {
                    val next = nextTail.getAndIncrement()
                    if (next < count) launchRacer(next, initialDelayMs = 0L)
                }
            } finally {
                active.decrementAndGet()
            }
        }
    }

    for (index in 0 until window) {
        launchRacer(index, initialDelayMs = if (index > 0) headStartMs else 0L)
    }
    yield()
    // Sliding window: at most a few timeout waves after the head-start stagger.
    val waves = ((count + window - 1) / window).coerceIn(1, 4)
    val absolute = headStartMs + timeoutMs * waves + 500L
    try {
        withTimeoutOrNull(absolute) {
            while (!winner.isCompleted && active.get() > 0) {
                delay(20)
            }
            if (winner.isCompleted) winner.await() else null
        }
    } finally {
        // Drop losing in-flight resolves so a fast winner is not gated on their timeouts.
        coroutineContext.cancelChildren()
    }
}
