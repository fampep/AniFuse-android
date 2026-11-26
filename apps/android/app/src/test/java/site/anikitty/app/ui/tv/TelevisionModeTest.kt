// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.tv

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelevisionModeTest {
    @Test
    fun phoneUiModeStaysOnTheHandsetLayout() {
        assertFalse(selectsTelevision(resourceFlag = false, uiMode = Configuration.UI_MODE_TYPE_NORMAL))
        assertFalse(
            selectsTelevision(
                resourceFlag = false,
                uiMode = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES,
            ),
        )
    }

    @Test
    fun televisionUiModeOrResourceFlagSelectsTheTvLayout() {
        assertTrue(selectsTelevision(resourceFlag = false, uiMode = Configuration.UI_MODE_TYPE_TELEVISION))
        assertTrue(
            selectsTelevision(
                resourceFlag = true,
                uiMode = Configuration.UI_MODE_TYPE_NORMAL,
            ),
        )
        assertTrue(
            selectsTelevision(
                resourceFlag = false,
                uiMode = Configuration.UI_MODE_TYPE_TELEVISION or Configuration.UI_MODE_NIGHT_YES,
            ),
        )
    }
}
