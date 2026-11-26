// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import site.anikitty.app.R
import site.anikitty.app.ui.theme.KittyMotion

/**
 * D-pad target for the television layout. Focus draws a white stroke and,
 * for posters, a slight scale. Center / Enter activates [onClick].
 * Stroke width changes inside one border modifier so focus does not recreate the node.
 */
@Composable
fun Modifier.tvActivate(
    onClick: () -> Unit,
    requester: FocusRequester? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    grow: Boolean = true,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val stroke = dimensionResource(R.dimen.tv_focus_stroke)
    val scale by animateFloatAsState(
        targetValue = if (grow && focused) 1.03f else 1f,
        animationSpec = tween(KittyMotion.duration(140)),
        label = "tvFocus",
    )
    val interaction = remember { MutableInteractionSource() }
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .onFocusChanged { focused = it.isFocused }
        .border(
            width = if (focused) stroke else 0.dp,
            color = if (focused) Color.White else Color.Transparent,
            shape = shape,
        )
        .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
        .clickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
}

/** White pill with a gap so the focus stroke stays visible against the fill. */
@Composable
fun TvPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    requester: FocusRequester? = null,
    icon: ImageVector? = null,
) {
    val body = tvSp(R.dimen.tv_body)
    Row(
        modifier = modifier
            .tvActivate(onClick, requester, shape = RoundedCornerShape(12.dp), grow = false)
            .padding(3.dp)
            .background(Color.White, RoundedCornerShape(9.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label,
            color = Color.Black,
            fontSize = body,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
