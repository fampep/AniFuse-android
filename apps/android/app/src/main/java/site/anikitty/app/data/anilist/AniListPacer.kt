// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.anilist

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Spaces AniList starts without holding the lock for the whole HTTP call.
 * Interactive catalog work runs ahead of list sync. A 429 penalty is visible
 * to every later caller, not only the coroutine that received it.
 */
internal class AniListPacer(
    private val minIntervalMs: Long = 700L,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val gate = Mutex()
    private var nextStartAt = 0L
    private var interactive = 0

    suspend fun <T> run(background: Boolean, block: suspend () -> T): T {
        while (true) {
            val wait = gate.withLock {
                if (background && interactive > 0) return@withLock WAIT
                if (!background) interactive++
                val now = clock()
                val start = maxOf(now, nextStartAt)
                nextStartAt = start + minIntervalMs
                start - now
            }
            if (wait == WAIT) {
                sleep(50)
                continue
            }
            if (wait > 0) sleep(wait)
            return try {
                block()
            } finally {
                if (!background) {
                    gate.withLock { interactive = (interactive - 1).coerceAtLeast(0) }
                }
            }
        }
    }

    suspend fun penalize(extraMs: Long) {
        if (extraMs <= 0L) return
        gate.withLock {
            val now = clock()
            nextStartAt = maxOf(nextStartAt, now + extraMs)
        }
    }

    private companion object {
        const val WAIT = -1L
    }
}
