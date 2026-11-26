// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.tv

import android.content.res.Configuration
import androidx.annotation.DimenRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import site.anikitty.app.R

/**
 * True on Android TV. The system selects [R.bool.is_television] from
 * `values-television` when the UI mode is television — the same qualifier
 * family as `layout-television`. Screens are Compose, so that bool (and the
 * television dimens) is what swaps the layout. A device that reports
 * [Configuration.UI_MODE_TYPE_TELEVISION] is included either way.
 * Phones and tablets stay on the default `false` resource.
 */
@Composable
@ReadOnlyComposable
fun isTelevision(): Boolean {
    val uiMode = LocalConfiguration.current.uiMode
    val flagged = LocalContext.current.resources.getBoolean(R.bool.is_television)
    return selectsTelevision(flagged, uiMode)
}

fun selectsTelevision(resourceFlag: Boolean, uiMode: Int): Boolean {
    val type = uiMode and Configuration.UI_MODE_TYPE_MASK
    return resourceFlag || type == Configuration.UI_MODE_TYPE_TELEVISION
}

/** `sp` dimens already include font scale in [android.content.res.Resources.getDimension]. */
@Composable
@ReadOnlyComposable
fun tvSp(@DimenRes id: Int): TextUnit {
    val px = LocalContext.current.resources.getDimension(id)
    val density = LocalDensity.current
    val scale = density.density * density.fontScale
    if (scale == 0f) return 18.sp
    return (px / scale).sp
}

suspend fun awaitTvFocus(requester: FocusRequester) {
    repeat(6) {
        val focused = runCatching {
            requester.requestFocus()
            true
        }.getOrDefault(false)
        if (focused) return
        delay(40)
    }
}
