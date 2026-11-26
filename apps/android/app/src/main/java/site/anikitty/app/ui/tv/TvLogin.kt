// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import site.anikitty.app.R
import site.anikitty.app.data.anilist.AniListOAuth
import site.anikitty.app.ui.components.KittyFace
import site.anikitty.app.ui.theme.KittyMuted

/** Sign-in for a remote. Same actions as the phone login, 10-foot type, D-pad focus. */
@Composable
fun TvLoginScreen(
    statusMessage: String?,
    onClearStatus: () -> Unit,
    onTryDemo: () -> Unit,
) {
    val context = LocalContext.current
    val signIn = remember { FocusRequester() }
    val title = tvSp(R.dimen.tv_title)
    val body = tvSp(R.dimen.tv_body)
    val row = tvSp(R.dimen.tv_row_title)
    LaunchedEffect(Unit) { awaitTvFocus(signIn) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(
                horizontal = dimensionResource(R.dimen.tv_overscan_horizontal),
                vertical = dimensionResource(R.dimen.tv_overscan_vertical),
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp).weight(1f, fill = false),
            verticalArrangement = Arrangement.Center,
        ) {
            KittyFace(
                modifier = Modifier.size(72.dp),
                waving = true,
                contentDescription = null,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "AniFuse",
                color = Color.White,
                fontSize = title,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Copyright 2026 fampep",
                color = KittyMuted,
                fontSize = body,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                "Sign in with AniList",
                color = Color.White,
                fontSize = row,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Scan the code with the AniFuse app on your phone, on the same Wi-Fi. Your lists and watch progress sign in here. Or sign in on this TV. In the AniList app settings, set the redirect to anikitty://anilist.",
                color = KittyMuted,
                fontSize = body,
            )
            Spacer(Modifier.height(22.dp))
            TvPill(
                label = "Sign in with AniList",
                requester = signIn,
                onClick = {
                    if (!statusMessage.isNullOrBlank()) onClearStatus()
                    AniListOAuth.openAuthorize(context)
                },
            )
            Spacer(Modifier.height(16.dp))
            TvPill(
                label = "Try demo",
                onClick = onTryDemo,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Sample library. Not your AniList account. Nothing is saved to your lists.",
                color = KittyMuted,
                fontSize = body,
            )
            if (!statusMessage.isNullOrBlank()) {
                Spacer(Modifier.height(20.dp))
                Text(statusMessage, color = KittyMuted, fontSize = body)
            }
        }
        Spacer(Modifier.width(48.dp))
        TvPairQr(LocalTvPairing.current?.offer)
        }
    }
}
