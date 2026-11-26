// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.tv

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.withTransaction
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import site.anikitty.app.AnikittyApp
import site.anikitty.app.data.anilist.AniListAuthStore
import site.anikitty.app.data.anilist.AniListClient
import site.anikitty.app.data.demo.DemoLibrary
import site.anikitty.app.data.local.WatchHistoryDao
import site.anikitty.app.data.local.applyWatchImport
import site.anikitty.app.data.local.readWatchProgressBackup
import site.anikitty.app.data.pair.LanPairListener
import site.anikitty.app.data.pair.LanPairServer
import site.anikitty.app.data.pair.PairHandoff
import site.anikitty.app.data.pair.PairOffer
import site.anikitty.app.data.pair.PairReply
import site.anikitty.app.data.pair.PairedDeviceStore
import site.anikitty.app.data.pair.newPairSecret
import site.anikitty.app.data.pair.pairUri
import site.anikitty.app.data.pair.thisDeviceName

class TvPairingState {
    var offer by mutableStateOf<PairOffer?>(null)
    var showCode by mutableStateOf(false)
}

val LocalTvPairing = compositionLocalOf<TvPairingState?> { null }

@Composable
fun TvPairingHost(
    aniList: AniListClient,
    auth: AniListAuthStore,
    historyDao: WatchHistoryDao,
    acceptSignIn: Boolean,
    onSignedIn: () -> Unit,
    onSignedOut: () -> Unit,
    state: TvPairingState,
) {
    val context = LocalContext.current
    val server = remember(context) { LanPairServer(context.applicationContext) }
    val signedIn = rememberUpdatedState(onSignedIn)
    val signedOut = rememberUpdatedState(onSignedOut)
    val alive = remember { AtomicBoolean(true) }
    DisposableEffect(server) {
        alive.set(true)
        server.setAcceptSignIn(acceptSignIn)
        val signedInRef = AtomicReference(signedIn)
        val signedOutRef = AtomicReference(signedOut)
        server.listener = object : LanPairListener {
            override fun onOffer(offer: PairOffer?) {
                if (alive.get()) state.offer = offer
            }
            override fun accept(handoff: PairHandoff): PairReply {
                signedInRef.set(signedIn)
                return acceptPairOnTv(context, aniList, auth, historyDao, handoff) {
                    if (alive.get()) signedInRef.get().value.invoke()
                }
            }
            override fun unpair(secret: String): Boolean {
                signedOutRef.set(signedOut)
                return unpairTv(context, auth, secret) {
                    if (alive.get()) signedOutRef.get().value.invoke()
                }
            }
        }
        server.start()
        onDispose {
            alive.set(false)
            server.listener = null
            server.stop()
            state.offer = null
        }
    }
    LaunchedEffect(acceptSignIn) {
        server.setAcceptSignIn(acceptSignIn)
    }
}

@Composable
fun ProvideTvPairing(state: TvPairingState?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTvPairing provides state, content = content)
}

@Composable
fun TvPairQr(offer: PairOffer?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (offer == null) {
            Text(
                "Connect this TV to Wi-Fi to show a code.",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 18.sp,
            )
            return
        }
        val uri = pairUri(offer)
        val matrix = remember(uri) {
            runCatching {
                QRCodeWriter().encode(
                    uri,
                    BarcodeFormat.QR_CODE,
                    256,
                    256,
                    mapOf(
                        EncodeHintType.MARGIN to 1,
                        EncodeHintType.CHARACTER_SET to "UTF-8",
                    ),
                )
            }.getOrNull()
        }
        if (matrix == null) {
            Text(offer.code, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            return
        }
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(12.dp)
                .semantics { contentDescription = "Pairing code ${offer.code}" },
        ) {
            Canvas(Modifier.size(196.dp)) {
                val cells = matrix.width
                val cell = size.minDimension / cells
                for (x in 0 until cells) {
                    for (y in 0 until cells) {
                        if (matrix.get(x, y)) {
                            drawRect(
                                Color.Black,
                                topLeft = Offset(x * cell, y * cell),
                                size = Size(cell, cell),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            offer.code,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp,
        )
    }
}

private fun acceptPairOnTv(
    context: android.content.Context,
    aniList: AniListClient,
    auth: AniListAuthStore,
    historyDao: WatchHistoryDao,
    handoff: PairHandoff,
    onSignedIn: () -> Unit,
): PairReply {
    val viewer = runBlocking { aniList.viewer(handoff.token) }
        ?: return PairReply(false, null, null, "AniList did not accept that sign-in")
    runCatching {
        val file = handoff.historyJson?.let { readWatchProgressBackup(it) } ?: return@runCatching
        val app = context.applicationContext as AnikittyApp
        runBlocking {
            app.database.withTransaction {
                applyWatchImport(
                    entries = file.entries,
                    current = { historyDao.get(it) },
                    write = { historyDao.upsert(it) },
                    skip = { DemoLibrary.find(it) != null },
                )
            }
        }
    }
    auth.saveViewer(handoff.token, viewer)
    val secret = newPairSecret()
    PairedDeviceStore(context).setLocalSession(secret, handoff.deviceName)
    Handler(Looper.getMainLooper()).post(onSignedIn)
    return PairReply(true, secret, thisDeviceName(context), null)
}

private fun unpairTv(
    context: android.content.Context,
    auth: AniListAuthStore,
    secret: String,
    onSignedOut: () -> Unit,
): Boolean {
    val store = PairedDeviceStore(context)
    if (!site.anikitty.app.data.pair.secretsMatch(store.localSecret(), secret)) return false
    store.setLocalSession(null, null)
    auth.clear()
    Handler(Looper.getMainLooper()).post(onSignedOut)
    return true
}

/** Reads the phone's watch rows for a one-time playhead copy. Episode totals still sync through AniList. */
suspend fun historyBackupForPair(historyDao: WatchHistoryDao): String? {
    val rows = historyDao.observeAll().first()
    return site.anikitty.app.data.local.watchProgressBackupForPair(rows)
}
