// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.anilist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AniListBatchPushTest {
    @Test
    fun chunksCoverEveryPendingRow() {
        assertEquals(emptyList<IntRange>(), pendingPushChunks(0))
        assertEquals(listOf(0 until 4), pendingPushChunks(4, chunkSize = 6))
        assertEquals(listOf(0 until 6, 6 until 10), pendingPushChunks(10, chunkSize = 6))
        assertEquals(14, ANILIST_PUSH_CHUNK)
    }

    @Test
    fun drainOrdersFreshestFirst() {
        data class Row(val id: Int, val at: Long)
        assertEquals(
            listOf(3, 1, 2),
            pendingPushesForDrain(
                listOf(Row(1, 100), Row(2, 50), Row(3, 200)),
            ) { it.at }.map { it.id },
        )
    }

    @Test
    fun batchMutationUsesAliasesAndEnumStatus() {
        val gql = buildSaveProgressBatchMutation(
            listOf(
                AniListBatchPushItem(mediaId = 21, progress = 5, status = "current"),
                AniListBatchPushItem(mediaId = 44, progress = 12, status = "COMPLETED"),
            ),
        )
        assertTrue(gql.contains("e0: SaveMediaListEntry(mediaId: 21"))
        assertTrue(gql.contains("status: CURRENT"))
        assertTrue(gql.contains("e1: SaveMediaListEntry(mediaId: 44"))
        assertTrue(gql.contains("status: COMPLETED"))
    }

    @Test
    fun batchParserKeepsOnlySuccessfulAliases() {
        val body = """{"data":{"e0":{"id":1},"e1":null,"e2":{"id":3}}}"""
        assertEquals(setOf(0, 2), parseSaveProgressBatchSuccess(body, expected = 3))
        assertEquals(emptySet<Int>(), parseSaveProgressBatchSuccess("{}", expected = 2))
    }

    @Test
    fun rejectedRowsRetryWhenBatchIsPartial() {
        data class Row(val id: Int)
        val chunk = listOf(Row(10), Row(20), Row(30))
        assertEquals(
            listOf(20, 30),
            pendingPushRejected(chunk, acceptedIds = setOf(10)) { it.id }.map { it.id },
        )
        assertTrue(pendingPushPartialSuccess(chunkSize = 3, acceptedCount = 1))
        assertTrue(!pendingPushPartialSuccess(chunkSize = 3, acceptedCount = 0))
        assertTrue(!pendingPushPartialSuccess(chunkSize = 3, acceptedCount = 3))
        assertTrue(!pendingPushPartialSuccess(chunkSize = 0, acceptedCount = 0))
    }

    @Test
    fun partialGraphqlBodyStillYieldsAcceptedMediaIds() {
        val items = listOf(
            AniListBatchPushItem(10, 1, "CURRENT"),
            AniListBatchPushItem(20, 2, "CURRENT"),
            AniListBatchPushItem(30, 3, "CURRENT"),
        )
        val body = """{"data":{"e0":{"id":1},"e1":null,"e2":{"id":3}},"errors":[{"message":"bad"}]}"""
        assertTrue(aniListBodyHasData(body))
        assertFalse(aniListBodyHasData("{}"))
        assertEquals(setOf(10, 30), acceptedMediaIdsFromBatch(body, items))
    }

    @Test
    fun retryPlanRebatchesThenCapsSingles() {
        val rejected = (1..10).map { AniListBatchPushItem(it, it, "CURRENT") }
        val plan = pendingPushRetryPlan(rejected, maxSingles = 3, rebatchSize = 4)
        assertEquals(4, plan.rebatch.size)
        assertEquals(3, plan.singles.size)
        assertEquals(3, plan.deferred.size)
        assertEquals(listOf(1, 2, 3, 4), plan.rebatch.map { it.mediaId })
        assertEquals(listOf(5, 6, 7), plan.singles.map { it.mediaId })
        assertEquals(listOf(8, 9, 10), plan.deferred.map { it.mediaId })
    }
}
