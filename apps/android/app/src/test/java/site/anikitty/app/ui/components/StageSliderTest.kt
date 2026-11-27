// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StageSliderTest {
    @Test
    fun restPoseIsFullSizeAndClear() {
        val pose = stageSlidePose(0f)
        assertEquals(0f, pose.parallax, 0.001f)
        assertEquals(1f, pose.scale, 0.001f)
        assertEquals(1f, pose.alpha, 0.001f)
        assertEquals(0f, pose.veil, 0.001f)
        assertEquals(0f, pose.tilt, 0.001f)
    }

    @Test
    fun swipeOffsetLagsScalesAndFadesWithTheFinger() {
        val pose = stageSlidePose(-0.5f)
        assertTrue("image lags a leftward swipe", pose.parallax > 0.1f)
        assertTrue(pose.scale in 0.90f..0.98f)
        assertTrue(pose.alpha in 0.65f..0.95f)
        assertTrue(pose.veil > 0.05f)
        assertTrue(pose.tilt > 0f)
        val far = stageSlidePose(-1f)
        assertTrue(far.scale < pose.scale)
        assertTrue(far.alpha < pose.alpha)
        assertEquals(0.34f, far.parallax, 0.001f)
    }

    @Test
    fun easeIsFlatAtTheEndsOfTheGesture() {
        assertEquals(0f, stageEase(0f), 0.001f)
        assertEquals(1f, stageEase(1f), 0.001f)
        assertTrue(stageEase(0.5f) == 0.5f)
        assertTrue(stageEase(0.2f) < 0.2f)
    }
}
