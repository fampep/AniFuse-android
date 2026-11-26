// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import site.anikitty.app.data.pair.DiscoveredTv
import site.anikitty.app.data.pair.PairOffer
import site.anikitty.app.data.pair.PairedDeviceStore
import site.anikitty.app.data.pair.PairedPeer
import site.anikitty.app.data.pair.discoverTvs
import site.anikitty.app.data.pair.isPairCode
import site.anikitty.app.data.pair.postUnpair
import site.anikitty.app.ui.theme.LocalKittyPalette
import site.anikitty.app.ui.tv.LocalTvPairing
import site.anikitty.app.ui.tv.TvPairQr
import site.anikitty.app.ui.tv.isTelevision

val LocalPairRequest = compositionLocalOf<(PairOffer) -> Unit> { {} }

/**
 * Pair or unpair a TV, and sign this device in or out of AniList.
 * The phone scans the TV. The TV shows the code.
 */
@Composable
internal fun DevicePairingPage(
    demo: Boolean,
    linked: Boolean,
    accountLabel: String,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    val palette = LocalKittyPalette.current
    val context = LocalContext.current
    val television = isTelevision()
    val pairing = LocalTvPairing.current
    val store = remember(context) { PairedDeviceStore(context) }
    var peers by remember { mutableStateOf(store.peers()) }
    var peerName by remember { mutableStateOf(store.localPeerName()) }
    var note by remember { mutableStateOf<String?>(null) }
    var finding by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<DiscoveredTv>>(emptyList()) }
    var code by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val requestPair = LocalPairRequest.current
    if (television && pairing != null) {
        DisposableEffect(pairing) {
            pairing.showCode = true
            onDispose { pairing.showCode = false }
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            when {
                demo -> "The demo library is not an AniList account."
                linked -> "This device is signed in as $accountLabel."
                else -> "This device is not signed in."
            },
            color = palette.text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            "Lists, favourites, and episode progress sync through AniList. Pairing also copies the playhead inside an episode.",
            color = palette.muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        if (demo) {
            SpaceCard(onClick = onSignOut, grabFocus = television, modifier = Modifier.fillMaxWidth()) {
                Text("Leave demo", color = palette.text, fontWeight = FontWeight.SemiBold)
            }
        } else if (linked) {
            SpaceCard(onClick = onSignOut, grabFocus = television, modifier = Modifier.fillMaxWidth()) {
                Text("Sign out", color = palette.text, fontWeight = FontWeight.SemiBold)
                Text(
                    "Signs this device out of AniList. Other devices stay signed in until you unpair them.",
                    color = palette.muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            SpaceCard(onClick = onSignIn, grabFocus = television, modifier = Modifier.fillMaxWidth()) {
                Text("Sign in with AniList", color = palette.text, fontWeight = FontWeight.SemiBold)
            }
        }
        if (television) {
            Text(
                "Scan this with the AniFuse app on your phone. Same Wi-Fi.",
                color = palette.muted,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            TvPairQr(pairing?.offer)
            peerName?.let { name ->
                Text(
                    "Paired from $name",
                    color = palette.text,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        } else {
            Text(
                "On the TV, open AniFuse and scan the code with your phone camera. Or find the TV here and type the code.",
                color = palette.muted,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            SpaceCard(
                onClick = {
                    if (finding) return@SpaceCard
                    finding = true
                    note = "Looking for a TV…"
                    scope.launch {
                        val tvs = withContext(Dispatchers.IO) { discoverTvs(context) }
                        found = tvs
                        finding = false
                        note = if (tvs.isEmpty()) "No TV found. Same Wi-Fi, and the code has to be on screen." else null
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (finding) "Looking…" else "Find a TV",
                    color = palette.text,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (found.isNotEmpty()) {
                BasicTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(6) },
                    textStyle = TextStyle(color = palette.text, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    cursorBrush = SolidColor(palette.text),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    decorationBox = { inner ->
                        Column {
                            if (code.isEmpty()) {
                                Text("Code on the TV", color = palette.muted, fontSize = 16.sp)
                            }
                            inner()
                        }
                    },
                )
                found.forEach { tv ->
                    SpaceCard(
                        onClick = {
                            if (!linked || demo) {
                                note = "Sign in with AniList on this phone first."
                                return@SpaceCard
                            }
                            if (!isPairCode(code)) {
                                note = "Type the 6-character code on the TV."
                                return@SpaceCard
                            }
                            requestPair(PairOffer(code.trim().uppercase(), tv.host, tv.port))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(tv.name, color = palette.text, fontWeight = FontWeight.SemiBold)
                        Text("Pair", color = palette.muted, fontSize = 13.sp)
                    }
                }
            }
            if (peers.isEmpty()) {
                Text(
                    "No TVs paired from this phone yet.",
                    color = palette.muted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            peers.forEach { peer ->
                SpaceCard(
                    onClick = {
                        scope.launch {
                            note = "Signing the TV out…"
                            val reply = withContext(Dispatchers.IO) {
                                postUnpair(peer.host, peer.port, peer.secret)
                            }
                            store.forgetPeer(peer.secret)
                            peers = store.peers()
                            note = if (reply.ok) {
                                "Signed ${peer.name} out."
                            } else {
                                "Removed ${peer.name} here. If it is still signed in, sign it out in the TV settings."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(peer.name, color = palette.text, fontWeight = FontWeight.SemiBold)
                    Text("Unpair", color = palette.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        if (!note.isNullOrBlank()) {
            Text(note ?: "", color = palette.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

internal fun rememberPeer(context: android.content.Context, name: String, offer: PairOffer, secret: String) {
    PairedDeviceStore(context).rememberPeer(
        PairedPeer(
            name = name,
            host = offer.host,
            port = offer.port,
            secret = secret,
            pairedAtEpochMs = System.currentTimeMillis(),
        ),
    )
}
