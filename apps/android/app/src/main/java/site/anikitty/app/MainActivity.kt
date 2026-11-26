// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import site.anikitty.app.cast.CastHardwareVolume
import site.anikitty.app.data.anilist.AniListOAuth
import site.anikitty.app.data.pair.PairOffer
import site.anikitty.app.data.pair.parsePairUri
import site.anikitty.app.R
import site.anikitty.app.ui.tv.selectsTelevision
import site.anikitty.app.ui.app.AppRoot
import site.anikitty.app.ui.player.PipAutoEnter
import site.anikitty.app.ui.theme.AniKittyTheme

/** AppCompatActivity so Chromecast MediaRouteButton / dialogs inflate correctly. */
class MainActivity : AppCompatActivity() {
    private var oauthToken by mutableStateOf<String?>(null)
    private var oauthError by mutableStateOf<String?>(null)
    private var requestDemo by mutableStateOf(false)
    private var requestBrowse by mutableStateOf(false)
    private var pendingPair by mutableStateOf<PairOffer?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        requestDemo = intent?.getBooleanExtra(EXTRA_DEMO, false) == true
        requestBrowse = intent?.getBooleanExtra(EXTRA_BROWSE, false) == true
        consumePairIntent(intent)
        consumeOAuthIntent(intent)
        val app = application as AnikittyApp
        setContent {
            AniKittyTheme {
                AppRoot(
                    session = app.session,
                    savedPassphrase = app.savedPassphrase,
                    aniList = app.aniList,
                    aniListAuth = app.aniListAuth,
                    aniListSync = app.aniListSync,
                    historyDao = app.database.watchHistoryDao(),
                    streamServers = app.streamServers,
                    anifuseApi = app.anifuseApi,
                    anifuseStore = app.anifuseStore,
                    pendingAniListToken = oauthToken,
                    pendingAniListError = oauthError,
                    onConsumedAniListToken = {
                        oauthToken = null
                        oauthError = null
                    },
                    requestDemo = requestDemo,
                    requestBrowse = requestBrowse,
                    pendingPair = pendingPair,
                    onConsumedPair = { pendingPair = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_DEMO, false)) requestDemo = true
        if (intent.getBooleanExtra(EXTRA_BROWSE, false)) requestBrowse = true
        consumePairIntent(intent)
        consumeOAuthIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PipAutoEnter.onUserLeaveHint(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val step = CastHardwareVolume.onStep
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> 1
            KeyEvent.KEYCODE_VOLUME_DOWN -> -1
            KeyEvent.KEYCODE_VOLUME_MUTE -> 0
            else -> null
        }
        if (step != null && direction != null) {
            if (direction == 0 && event.repeatCount > 0) return true
            if (step(direction)) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun consumePairIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "anikitty" || data.host != "pair") return
        val television = selectsTelevision(
            resourceFlag = resources.getBoolean(R.bool.is_television),
            uiMode = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK,
        )
        if (!television) {
            pendingPair = parsePairUri(data.toString())
        }
        intent.data = null
    }

    private fun consumeOAuthIntent(intent: Intent?) {
        if (!AniListOAuth.matchesCallback(intent)) return
        // Only honor callbacks after Account → Link AniList (short-lived expectation + state).
        val expectedState = AniListOAuth.consumeLinkStarted(this)
        if (expectedState.isNullOrBlank()) {
            oauthToken = null
            oauthError = "Unexpected AniList callback — tap Sign in with AniList first"
            intent?.data = null
            return
        }
        runCatching {
            oauthToken = AniListOAuth.parseRedirect(intent?.data, expectedState)
                ?: error("No access token in AniList redirect")
            oauthError = null
        }.onFailure { e ->
            oauthToken = null
            oauthError = e.message ?: "AniList link failed"
        }
        intent?.data = null
    }

    companion object {
        const val EXTRA_DEMO = "demo"
        /** Public AniList catalog. Does not sign in and does not load the sample library. */
        const val EXTRA_BROWSE = "browse"
    }
}
