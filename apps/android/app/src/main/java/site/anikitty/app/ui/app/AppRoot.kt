// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import site.anikitty.app.data.pair.PairHandoff
import site.anikitty.app.data.pair.PairOffer
import site.anikitty.app.data.pair.PairedDeviceStore
import site.anikitty.app.data.pair.postPair
import site.anikitty.app.data.pair.thisDeviceName
import site.anikitty.app.data.anifuse.AnifuseApi
import site.anikitty.app.data.anifuse.AnifuseApiStore
import site.anikitty.app.ui.tv.LocalTvPairing
import site.anikitty.app.ui.tv.TvLoginScreen
import site.anikitty.app.ui.tv.TvPairingHost
import site.anikitty.app.ui.tv.TvPairingState
import site.anikitty.app.ui.tv.awaitTvFocus
import site.anikitty.app.ui.tv.historyBackupForPair
import site.anikitty.app.ui.tv.isTelevision
import java.util.Locale
import site.anikitty.app.data.anilist.AniListAuthStore
import site.anikitty.app.data.anilist.AniListClient
import site.anikitty.app.data.anilist.AniListOAuth
import site.anikitty.app.data.anilist.AniListSync
import site.anikitty.app.data.auth.SavedPassphraseStore
import site.anikitty.app.data.auth.SessionStore
import site.anikitty.app.data.local.WatchHistoryDao
import site.anikitty.app.data.servers.ServerCatalog
import site.anikitty.app.ui.components.PrismMark
import site.anikitty.app.ui.theme.KittyAccent
import site.anikitty.app.ui.theme.KittyHaptics
import site.anikitty.app.ui.theme.LookState
import site.anikitty.app.ui.theme.prefersReducedMotion
import site.anikitty.app.ui.theme.KittyAtmosphere
import site.anikitty.app.ui.theme.KittyGlassShape
import site.anikitty.app.ui.theme.KittyMuted
import site.anikitty.app.ui.theme.KittyOnAccent
import site.anikitty.app.ui.theme.KittyOutlineSoft
import site.anikitty.app.ui.theme.KittySurface
import site.anikitty.app.ui.theme.KittyText
import site.anikitty.app.ui.theme.LocalDemoMode
import site.anikitty.app.ui.theme.kittyGlass

/**
 * AniList sign-in is the account. Lists, progress, and profile come from AniList.
 */
@Composable
fun AppRoot(
    session: SessionStore,
    @Suppress("UNUSED_PARAMETER") savedPassphrase: SavedPassphraseStore,
    aniList: AniListClient,
    aniListAuth: AniListAuthStore,
    aniListSync: AniListSync,
    historyDao: WatchHistoryDao,
    streamServers: ServerCatalog,
    anifuseApi: AnifuseApi,
    anifuseStore: AnifuseApiStore,
    pendingAniListToken: String? = null,
    pendingAniListError: String? = null,
    onConsumedAniListToken: () -> Unit = {},
    requestDemo: Boolean = false,
    requestBrowse: Boolean = false,
    pendingPair: PairOffer? = null,
    onConsumedPair: () -> Unit = {},
) {
    var signedIn by remember { mutableStateOf(aniListAuth.isLinked) }
    var demo by rememberSaveable { mutableStateOf(false) }
    var browse by rememberSaveable { mutableStateOf(false) }
    var showSplash by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(requestDemo) {
        if (requestDemo) demo = true
    }
    LaunchedEffect(requestBrowse) {
        if (requestBrowse) browse = true
    }
    val showApp = signedIn || demo || browse || !pendingAniListToken.isNullOrBlank()

    if (showSplash) {
        SplashScreen(onFinished = { showSplash = false })
        return
    }

    val context = LocalContext.current
    LookState.ensure(context)
    val television = isTelevision()
    val pairing = remember { TvPairingState() }
    var pairRequest by remember { mutableStateOf(pendingPair) }
    LaunchedEffect(pendingPair) {
        if (pendingPair != null) pairRequest = pendingPair
    }
    LaunchedEffect(pairRequest) {
        if (pairRequest != null && !aniListAuth.isLinked) browse = false
    }
    if (television) {
        TvPairingHost(
            aniList = aniList,
            auth = aniListAuth,
            historyDao = historyDao,
            acceptSignIn = !showApp || pairing.showCode,
            onSignedIn = { signedIn = true },
            onSignedOut = {
                session.clear()
                aniListAuth.clear()
                PairedDeviceStore(context).setLocalSession(null, null)
                signedIn = false
                demo = false
            },
            state = pairing,
        )
    }
    val loginStatus = pendingAniListError ?: pairRequest?.let {
        if (!signedIn) "Sign in with AniList to finish pairing the TV." else null
    }
    CompositionLocalProvider(
        LocalDemoMode provides demo,
        LocalTvPairing provides if (television) pairing else null,
        LocalPairRequest provides { offer -> pairRequest = offer },
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
    if (!showApp) {
        if (television) {
            TvLoginScreen(
                statusMessage = loginStatus,
                onClearStatus = onConsumedAniListToken,
                onTryDemo = { demo = true },
            )
        } else {
            LoginScreen(
                statusMessage = loginStatus,
                onClearStatus = onConsumedAniListToken,
                onTryDemo = { demo = true },
            )
        }
    } else {
        LocalFirstApp(
            aniList = aniList,
            aniListAuth = aniListAuth,
            aniListSync = aniListSync,
            historyDao = historyDao,
            streamServers = streamServers,
            anifuseApi = anifuseApi,
            anifuseStore = anifuseStore,
            accountLabel = when {
                demo && !signedIn -> "Demo library"
                !signedIn -> "Not signed in"
                else -> aniListAuth.username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "@anilist"
            },
            pendingAniListToken = if (demo) null else pendingAniListToken,
            pendingAniListError = if (demo) null else pendingAniListError,
            onConsumedAniListToken = onConsumedAniListToken,
            onAniListReady = { signedIn = true },
            demo = demo,
            onSignOut = {
                if (demo) {
                    demo = false
                } else {
                    session.clear()
                    aniListAuth.clear()
                    PairedDeviceStore(context).setLocalSession(null, null)
                    signedIn = false
                }
            },
        )
    }
        if (!LookState.welcomed) {
            WelcomeOverlay(
                television = television,
                onDismiss = { LookState.setWelcomed(context) },
            )
        }
        val request = pairRequest
        if (request != null && demo) {
            PairNotice(
                title = "Leave the demo to pair the TV",
                body = "The demo library is not your AniList account.",
                confirm = "Leave demo",
                onConfirm = { demo = false },
                onDismiss = {
                    pairRequest = null
                    onConsumedPair()
                },
            )
        } else if (request != null && signedIn && !television) {
            PairConfirm(
                offer = request,
                token = aniListAuth.accessToken.orEmpty(),
                accountLabel = aniListAuth.username?.let { "@$it" } ?: "your AniList account",
                historyDao = historyDao,
                onDismiss = {
                    pairRequest = null
                    onConsumedPair()
                },
                onPaired = {
                    pairRequest = null
                    onConsumedPair()
                },
            )
        }
    }
    }
}

@Composable
private fun PairNotice(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(28.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(KittySurface)
                .padding(24.dp),
        ) {
            Text(title, color = KittyText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(body, color = KittyMuted)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = KittyAccent, contentColor = KittyOnAccent),
            ) { Text(confirm) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onDismiss) { Text("Not now") }
        }
    }
}

@Composable
private fun PairConfirm(
    offer: PairOffer,
    token: String,
    accountLabel: String,
    historyDao: WatchHistoryDao,
    onDismiss: () -> Unit,
    onPaired: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    PairNotice(
        title = "Sign this TV in as $accountLabel?",
        body = error ?: "Lists, favourites, and watch progress sync through AniList. The code is ${offer.code}.",
        confirm = if (sending) "Pairing…" else "Pair",
        onConfirm = {
            if (sending || token.isBlank()) {
                error = "Sign in with AniList on this phone first."
            } else {
            sending = true
            error = null
            scope.launch {
                val history = withContext(Dispatchers.IO) { historyBackupForPair(historyDao) }
                val reply = withContext(Dispatchers.IO) {
                    postPair(
                        offer,
                        PairHandoff(
                            code = offer.code,
                            token = token,
                            deviceName = thisDeviceName(context),
                            historyJson = history,
                        ),
                    )
                }
                if (reply.ok && !reply.secret.isNullOrBlank()) {
                    rememberPeer(context, reply.name ?: "TV", offer, reply.secret)
                    onPaired()
                } else {
                    sending = false
                    error = reply.error ?: "Could not pair"
                }
            }
            }
        },
        onDismiss = { if (!sending) onDismiss() },
    )
}

@Composable
private fun LoginScreen(
    statusMessage: String?,
    onClearStatus: () -> Unit,
    onTryDemo: () -> Unit,
) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize()) {
        KittyAtmosphere()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PrismMark(
                modifier = Modifier.size(96.dp),
                glow = true,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "AniFuse",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = KittyText,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Copyright 2026 fampep",
                style = MaterialTheme.typography.bodyMedium,
                color = KittyMuted,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(28.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(KittyGlassShape)
                    .background(KittySurface)
                    .border(1.dp, KittyOutlineSoft, KittyGlassShape)
                    .padding(18.dp),
            ) {
                Text(
                    "Sign in with AniList",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = KittyText,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Your watching list, completed shows, and profile come from AniList. In the AniList app settings, set the redirect to anikitty://anilist.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KittyMuted,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (!statusMessage.isNullOrBlank()) onClearStatus()
                        AniListOAuth.openAuthorize(context)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KittyAccent,
                        contentColor = KittyOnAccent,
                    ),
                ) {
                    Text("Sign in with AniList", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onTryDemo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, KittyOutlineSoft),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KittyText),
                ) {
                    Text("Try demo", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Sample library. Not your AniList account. Nothing is saved to your lists.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KittyMuted,
                )
            }
            if (!statusMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = KittyMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun WelcomeOverlay(television: Boolean, onDismiss: () -> Unit) {
    val reduced = prefersReducedMotion()
    val view = LocalView.current
    var shown by remember { mutableStateOf(reduced) }
    var letsGoFocused by remember { mutableStateOf(false) }
    val letsGo = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { shown = true }
    androidx.compose.runtime.LaunchedEffect(television) {
        if (!television) return@LaunchedEffect
        awaitTvFocus(letsGo)
    }
    val rise by animateFloatAsState(
        targetValue = if (shown) 0f else 28f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "welcome",
    )
    val danish = Locale.getDefault().language == "da"
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(if (television) Modifier.focusProperties { canFocus = false } else Modifier)
            .clickable(onClick = {
                KittyHaptics.perform(view, KittyHaptics.Kind.Confirm)
                onDismiss()
            }),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .graphicsLayer {
                    translationY = if (reduced) 0f else rise
                    alpha = if (reduced) 1f else (1f - (rise / 28f)).coerceIn(0f, 1f)
                }
                .kittyGlass(shape = RoundedCornerShape(24.dp), elevated = true)
                .then(if (television) Modifier.focusProperties { canFocus = false } else Modifier)
                .clickable(onClick = {})
                .padding(horizontal = if (television) 40.dp else 26.dp, vertical = if (television) 36.dp else 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PrismMark(
                modifier = Modifier.size(92.dp),
                glow = true,
                contentDescription = "AniFuse",
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "AniFuse",
                color = Color.White,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.6).sp,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                if (danish) "Hej. Klar til næste episode?" else "Hi. Ready for the next episode?",
                color = Color.White.copy(alpha = 0.86f),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = {
                    KittyHaptics.perform(view, KittyHaptics.Kind.Confirm)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (television) 56.dp else 48.dp)
                    .then(
                        if (television) {
                            Modifier
                                .focusRequester(letsGo)
                                .onFocusChanged { letsGoFocused = it.isFocused }
                                .border(
                                    width = if (letsGoFocused) 3.dp else 0.dp,
                                    color = if (letsGoFocused) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(16.dp),
                                )
                        } else {
                            Modifier
                        },
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KittyAccent,
                    contentColor = KittyOnAccent,
                ),
            ) {
                Text(if (danish) "Lad os" else "Let's go", fontWeight = FontWeight.Bold)
            }
        }
    }
}
