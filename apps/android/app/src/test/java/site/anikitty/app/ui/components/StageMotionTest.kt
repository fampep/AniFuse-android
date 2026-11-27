// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StageMotionTest {
    @Test
    fun aRestingSwipeDoesNotMoveTheStage() {
        assertEquals(StageBackPose.Rest, stageBackPose(0f, STAGE_EDGE_LEFT))
    }

    @Test
    fun predictiveBackUsesTheSharedAxisTravel() {
        val pose = stageBackPose(1f, STAGE_EDGE_LEFT)
        assertEquals(STAGE_BACK_TRAVEL, pose.translationFraction, 0.0001f)
        assertTrue(pose.alpha < 1f)
        assertTrue(pose.scale < 1f)
        val fromRight = stageBackPose(1f, STAGE_EDGE_RIGHT)
        assertEquals(-STAGE_BACK_TRAVEL, fromRight.translationFraction, 0.0001f)
    }

    @Test
    fun reducedMotionSnapsTheStage() {
        assertEquals(StageBackPose.Rest, stageBackPose(0.8f, STAGE_EDGE_LEFT, reduced = true))
    }

    @Test
    fun progressIsClamped() {
        val over = stageBackPose(4f, STAGE_EDGE_LEFT)
        assertEquals(STAGE_BACK_TRAVEL, over.translationFraction, 0.0001f)
    }
}
