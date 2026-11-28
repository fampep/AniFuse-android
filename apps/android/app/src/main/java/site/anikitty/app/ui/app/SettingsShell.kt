// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import site.anikitty.app.ui.icons.FluentuiSystemIconsAlertOutline
import site.anikitty.app.ui.icons.FluentuiSystemIconsArrowSync
import site.anikitty.app.ui.icons.FluentuiSystemIconsMoviesAndTvOutline
import site.anikitty.app.ui.icons.FluentuiSystemIconsPlayFilled
import site.anikitty.app.ui.components.stageGlassPlate
import site.anikitty.app.ui.theme.KittyGlass
import site.anikitty.app.ui.theme.kittyGlassSoft
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import site.anikitty.app.BuildConfig
import site.anikitty.app.data.anifuse.AnifuseApi
import site.anikitty.app.data.anifuse.AnifuseApiStore
import site.anikitty.app.data.anifuse.AnifuseHealth
import site.anikitty.app.data.anilist.AniListAuthStore
import site.anikitty.app.data.anilist.AniListClient
import site.anikitty.app.data.friendlyFailure
import site.anikitty.app.data.anilist.AniListOAuth
import site.anikitty.app.data.anilist.AniListSync
import site.anikitty.app.data.anilist.AniListSyncPhase
import site.anikitty.app.data.anilist.aniListSyncResultLabel
import site.anikitty.app.data.servers.ServerCatalog
import site.anikitty.app.data.servers.sourcesPageSubtitle
import site.anikitty.app.data.extensions.HttpExtensionResolver
import site.anikitty.app.ui.components.ExtensionIcon
import site.anikitty.app.ui.icons.FluentuiSystemIconsBookmarkOutline
import site.anikitty.app.ui.icons.FluentuiSystemIconsHomeOutline
import site.anikitty.app.ui.icons.FluentuiSystemIconsPersonOutline
import site.anikitty.app.ui.icons.FluentuiSystemIconsSettingsOutline
import site.anikitty.app.ui.player.speedLabel
import site.anikitty.app.ui.tv.isTelevision
import site.anikitty.app.ui.theme.KittyAccent
import site.anikitty.app.ui.theme.KittyOnAccent
import site.anikitty.app.ui.theme.LocalKittyPalette
import site.anikitty.app.ui.theme.LookState

@Composable
internal fun LookSettings(
    onExport: () -> Unit,
    onPickImport: () -> Unit,
    importNote: String?,
) {
    val context = LocalContext.current
    val palette = LocalKittyPalette.current
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Appearance", color = palette.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(
            "AniFuse — Discover. Stream. Enjoy. The default theme is black and white. Anime artwork is the only colour.",
            color = palette.muted,
            fontSize = 13.sp,
        )
        Text("Surface", color = palette.muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemeModeChip(
                label = "Soft black",
                detail = "Lifted surfaces",
                selected = LookState.look != "amoled",
                onClick = { LookState.setLook(context, "glass") },
                modifier = Modifier.weight(1f),
            )
            ThemeModeChip(
                label = "True black",
                detail = "Flat #080808",
                selected = LookState.look == "amoled",
                onClick = { LookState.setLook(context, "amoled") },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Remind me about shows I left",
                color = palette.text,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = LookState.abandonedReminders,
                onCheckedChange = { LookState.setAbandonedReminders(context, it) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Weekly goal · ${LookState.weekGoalMinutes} min",
                color = palette.text,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                "−",
                color = palette.text,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        LookState.setWeekGoal(context, LookState.weekGoalMinutes - 15)
                    }
                    .wrapContentHeight(),
            )
            Text(
                "+",
                color = palette.text,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        LookState.setWeekGoal(context, LookState.weekGoalMinutes + 15)
                    }
                    .wrapContentHeight(),
            )
        }
        Button(
            onClick = onExport,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = palette.onAccent,
            ),
        ) {
            Text("Export watch progress", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = onPickImport,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, palette.hairline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.text),
        ) {
            Text("Import watch progress", fontWeight = FontWeight.Bold)
        }
        Text(
            "A newer save on this phone stays. This does not change AniList.",
            color = palette.muted,
            fontSize = 12.sp,
        )
        importNote?.let { note ->
            Text(note, color = palette.text, fontSize = 13.sp)
        }
        val keyPrefs = remember { context.getSharedPreferences("anikitty_keys", 0) }
        var tmdbKey by remember { mutableStateOf(keyPrefs.getString("tmdb", "") ?: "") }
        var fanartKey by remember { mutableStateOf(keyPrefs.getString("fanart", "") ?: "") }
        Text("Backdrop keys", color = palette.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(
            "Optional. A key built into the app is used when a field is empty. An entry here replaces it.",
            color = palette.muted,
            fontSize = 12.sp,
        )
        OutlinedTextField(
            value = tmdbKey,
            onValueChange = {
                tmdbKey = it
                keyPrefs.edit().putString("tmdb", it.trim()).apply()
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("TMDB API key") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = palette.text,
                unfocusedTextColor = palette.text,
                focusedBorderColor = palette.accent,
                unfocusedBorderColor = palette.hairline,
                focusedLabelColor = palette.muted,
                unfocusedLabelColor = palette.muted,
            ),
        )
        OutlinedTextField(
            value = fanartKey,
            onValueChange = {
                fanartKey = it
                keyPrefs.edit().putString("fanart", it.trim()).apply()
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("fanart.tv API key") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = palette.text,
                unfocusedTextColor = palette.text,
                focusedBorderColor = palette.accent,
                unfocusedBorderColor = palette.hairline,
                focusedLabelColor = palette.muted,
                unfocusedLabelColor = palette.muted,
            ),
        )
        TmdbCredit()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AniListProfileCard(
    auth: AniListAuthStore,
    fallbackName: String,
    syncLabel: String?,
    pendingPushCount: Int = 0,
    onOpenList: (String) -> Unit = {},
    onSignOut: () -> Unit = {},
) {
    val palette = LocalKittyPalette.current
    val name = auth.username?.takeIf { it.isNotBlank() } ?: fallbackName
    val days = auth.minutesWatched / 1440
    val score = if (auth.meanScore <= 0f) "—" else auth.meanScore.toInt().toString()
    val glassSyncLabel = cloudSyncStatusLabel(syncLabel, pendingPushCount)
    // Phase busy only — SYNC · N must not disable the Sync control.
    val syncing = cloudSyncIsBusy(syncLabel)
    val onBanner = !auth.bannerUrl.isNullOrBlank()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SpaceLabel("PROFILE", padded = false)
            Text(
                text = "Logout",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = palette.muted,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .spacePress(onSignOut),
            )
        }
        val cardShape = RoundedCornerShape(18.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(cardShape)
                .background(if (onBanner) Color(0xCC0A0A0C) else KittyGlass)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.14f), cardShape)
                .border(0.5.dp, if (onBanner) Color.White.copy(alpha = 0.08f) else palette.hairline, cardShape),
        ) {
            if (!auth.bannerUrl.isNullOrBlank()) {
                AsyncImage(
                    model = auth.bannerUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = 0.22f },
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Black.copy(alpha = 0.92f), Color.Black.copy(alpha = 0.45f)),
                            ),
                        ),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!auth.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = auth.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(palette.surface),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            name.take(1).uppercase(),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        color = if (onBanner) Color.White else palette.text,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = glassSyncLabel,
                        color = if (onBanner) Color.White.copy(alpha = if (syncing) 1f else 0.72f) else palette.muted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        val aboutPlain = remember(auth.about) { stripAniListAbout(auth.about) }
        if (aboutPlain.isNotBlank()) {
            Text(
                aboutPlain,
                color = palette.muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        SpaceLabel("STATS")
        SpaceCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ProfileStat(auth.animeCount.toString(), "Anime")
                    StatDivider()
                    ProfileStat(auth.episodesWatched.toString(), "Episodes")
                    StatDivider()
                    ProfileStat(if (auth.minutesWatched > 0) days.toString() else "—", "Days")
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.hairline))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ProfileStat(auth.watchingCount.toString(), "Watching")
                    StatDivider()
                    ProfileStat(auth.completedCount.toString(), "Completed")
                    StatDivider()
                    ProfileStat(score, "Score")
                }
            }
        }
        SpaceLabel("LISTS")
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SpaceChip("Watching (${auth.watchingCount})", selected = false) { onOpenList("watching") }
            SpaceChip("Completed (${auth.completedCount})", selected = false) { onOpenList("done") }
            SpaceChip("Planning (${auth.planningCount})", selected = false) { onOpenList("plan") }
            SpaceChip("Paused (${auth.pausedCount})", selected = false) { onOpenList("paused") }
            SpaceChip("Dropped (${auth.droppedCount})", selected = false) { onOpenList("dropped") }
        }
        if (!syncLabel.isNullOrBlank() && !syncing) {
            Text(
                syncLabel,
                color = palette.muted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatDivider() {
    Box(
        Modifier
            .padding(vertical = 6.dp)
            .width(1.dp)
            .height(28.dp)
            .background(LocalKittyPalette.current.hairline),
    )
}

@Composable
internal fun androidx.compose.foundation.layout.RowScope.ProfileStat(
    value: String,
    label: String,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val palette = LocalKittyPalette.current
        Text(
            value,
            color = palette.text,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
        )
        Text(
            label,
            color = palette.muted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
internal fun SettingsFeed(
    demo: Boolean = false,
    catalog: ServerCatalog,
    aniList: AniListClient,
    aniListAuth: AniListAuthStore,
    aniListSync: AniListSync,
    anifuseApi: AnifuseApi,
    anifuseStore: AnifuseApiStore,
    accountLabel: String,
    syncLabel: String?,
    pendingPushCount: Int = 0,
    onSyncLabel: (String?) -> Unit,
    linkStatus: String?,
    onLinkStatus: (String?) -> Unit,
    linkTick: Int,
    onLinkTick: () -> Unit,
    onAccountReady: () -> Unit = {},
    onSignOut: () -> Unit,
    onOpenStore: () -> Unit,
    onClose: () -> Unit = {},
    onOpenBuilds: () -> Unit,
    onOpenProviders: () -> Unit = {},
    onOpenLibrary: (String) -> Unit = {},
    onOpenInbox: () -> Unit = {},
    homeLayout: HomeLayoutStore,
    openAccountTick: Int = 0,
    onExportProgress: () -> Unit = {},
    onImportProgress: suspend (String) -> String = { "Import isn't available" },
) {
    val palette = LocalKittyPalette.current
    val selectedId by catalog.selectedId.collectAsState()
    val resolvers by catalog.resolvers.collectAsState()
    val installedExt by catalog.extensions.installed.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var extensionStatus by remember { mutableStateOf<String?>(null) }
    val importProgress by rememberUpdatedState(onImportProgress)
    var importNote by remember { mutableStateOf<String?>(null) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().use { reader ->
                            val out = StringBuilder()
                            val buf = CharArray(8192)
                            while (out.length <= 2_000_000) {
                                val count = reader.read(buf)
                                if (count < 0) break
                                val room = 2_000_001 - out.length
                                out.append(buf, 0, minOf(count, room))
                                if (count > room) break
                            }
                            out.toString()
                        }
                    }
                }.getOrNull()
            }
            importNote = withContext(Dispatchers.IO) {
                if (text == null) {
                    "Couldn't read that file"
                } else {
                    importProgress(text)
                }
            }
        }
    }

    // linkTick bumps after save/clear so Compose re-reads prefs-backed isLinked.
    val isLinked = linkTick.let { aniListAuth.isLinked }
    var page by rememberSaveable { mutableStateOf("hub") }
    LaunchedEffect(openAccountTick) {
        if (openAccountTick > 0) page = "account"
    }
    BackHandler {
        if (page != "hub") page = "hub" else onClose()
    }
    val selectedName = resolvers.firstOrNull { it.server.id == selectedId }?.server?.name ?: "None selected"
    val homeShown = homeLayout.sections().count { it.enabled }
    val television = isTelevision()
    val apiEndpoint by anifuseStore.baseUrl.collectAsState()
    val disabledExtIds by catalog.extensions.disabled.collectAsState()
    val coldKeys = remember(resolvers, selectedId, disabledExtIds) { catalog.coldProviderKeys() }
    // Disabled extensions leave the failover chain but stay listed on the
    // sources page so they can be re-enabled.
    val sourceRows = remember(resolvers, installedExt, disabledExtIds) {
        val enabledIds = resolvers.map { it.server.id }.toSet()
        buildList {
            // Live sites first (including ones the user toggled off).
            catalog.bundledServers.forEach { add(it) }
            resolvers.forEach { resolver ->
                if (resolver.server.id !in catalog.bundledSiteIds) add(resolver.server)
            }
            installedExt.forEach { manifest ->
                if (manifest.id !in enabledIds && manifest.id !in catalog.bundledSiteIds) {
                    add(HttpExtensionResolver(manifest).server)
                }
            }
        }.distinctBy { it.id }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalKittyPalette.current.page)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(bottom = if (television) 24.dp else 100.dp),
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val back = targetState == "hub"
                val dir = if (back) -1 else 1
                (slideInHorizontally(tween(280)) { it / 5 * dir } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(200)) { -it / 6 * dir } + fadeOut(tween(150)))
            },
            label = "settingsPage",
        ) { current ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
        LazyColumn(
            modifier = if (television && current != "hub") {
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxHeight()
            } else {
                Modifier.fillMaxSize()
            },
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (current) {
                "hub" -> {
                    item {
                        SpacePageHeader(
                            title = "Profile",
                            subtitle = if (demo) {
                                "Sample library. Not your AniList account."
                            } else {
                                "AniFuse — Discover. Stream. Enjoy."
                            },
                            onBack = onClose,
                        )
                    }
                    if (isLinked && !demo) {
                        item {
                            AniListProfileCard(
                                auth = aniListAuth,
                                fallbackName = accountLabel.ifBlank { "AniList" },
                                syncLabel = syncLabel,
                                pendingPushCount = pendingPushCount,
                                onOpenList = onOpenLibrary,
                                onSignOut = onSignOut,
                            )
                        }
                    }
                    item {
                        SettingsHubBody(
                            wide = television,
                            demo = demo,
                            accountDetail = when {
                                demo -> "Demo library"
                                isLinked -> aniListAuth.username?.takeIf { it.isNotBlank() }?.let { "@$it" }
                                    ?: accountLabel.ifBlank { "Signed in" }
                                else -> "Not signed in"
                            },
                            avatarUrl = linkTick.let { aniListAuth.avatarUrl },
                            homeShown = homeShown,
                            sourceName = selectedName,
                            apiEndpoint = apiEndpoint,
                            onOpen = { page = it },
                            onOpenInbox = onOpenInbox,
                            onOpenDownloads = { onOpenLibrary("downloads") },
                        )
                    }
                }
                "devices" -> {
                    item {
                        SpacePageHeader(
                            title = "Devices",
                            subtitle = "Sign in, sign out, and pair a TV on the same Wi-Fi.",
                            onBack = { page = "hub" },
                        )
                    }
                    item {
                        DevicePairingPage(
                            demo = demo,
                            linked = isLinked,
                            accountLabel = when {
                                demo -> "Demo library"
                                isLinked -> aniListAuth.username?.takeIf { it.isNotBlank() }?.let { "@$it" }
                                    ?: accountLabel.ifBlank { "Signed in" }
                                else -> "Not signed in"
                            },
                            onSignIn = { AniListOAuth.openAuthorize(context) },
                            onSignOut = onSignOut,
                        )
                    }
                }
                "account" -> {
                    item {
                        SpacePageHeader(
                            title = "Account",
                            subtitle = when {
                                demo -> "Sample library. Not your AniList account."
                                isLinked -> "Your AniList profile."
                                else -> "Sign in to load your lists."
                            },
                            onBack = { page = "hub" },
                        )
                    }
                    if (demo) {
                        item {
                            SpaceCard(onClick = onSignOut) {
                                Text(
                                    "Leave demo",
                                    color = palette.text,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                )
                                Text(
                                    "Returns to sign-in. Your AniList account, if you have one, stays untouched.",
                                    color = palette.text.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    } else if (isLinked) {
                        item {
                            AniListProfileCard(
                                auth = aniListAuth,
                                fallbackName = accountLabel.ifBlank { "AniList" },
                                syncLabel = syncLabel,
                                pendingPushCount = pendingPushCount,
                                onOpenList = onOpenLibrary,
                                onSignOut = onSignOut,
                            )
                        }
                        item {
                            Button(
                                onClick = {
                                    scope.launch {
                                        onSyncLabel(cloudSyncPhaseLabel(AniListSyncPhase.Pushing))
                                        onSyncLabel(
                                            runCatching {
                                                val result = aniListSync.pull(
                                                    forceRefresh = true,
                                                    onPhase = { onSyncLabel(cloudSyncPhaseLabel(it)) },
                                                )
                                                if (result.frontierWarms.isNotEmpty()) {
                                                    val audio = context.getSharedPreferences(
                                                        "anikitty_watch",
                                                        android.content.Context.MODE_PRIVATE,
                                                    ).getString("audio", "sub")
                                                        ?.takeIf { it == "dub" } ?: "sub"
                                                    scope.launch {
                                                        site.anikitty.app.data.servers.warmFrontierContinues(
                                                            catalog = catalog,
                                                            targets = result.frontierWarms,
                                                            audio = audio,
                                                            appContext = context.applicationContext,
                                                        )
                                                    }
                                                }
                                                aniListSyncResultLabel(result)
                                            }.getOrElse { e -> friendlyFailure(e.message) },
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.accent,
                                    contentColor = palette.onAccent,
                                ),
                            ) { Text("Sync now", fontWeight = FontWeight.Bold) }
                            if (!linkStatus.isNullOrBlank()) {
                                Text(
                                    linkStatus,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.text.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                )
                            }
                        }
                    } else {
                        item {
                            SpaceCard(onClick = { AniListOAuth.openAuthorize(context) }) {
                                Text(
                                    "Connect AniList",
                                    color = palette.text,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                )
                                Text(
                                    "Your list and the next episode come back with you.",
                                    color = palette.text.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            if (!linkStatus.isNullOrBlank()) {
                                Text(
                                    linkStatus,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.text.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
                "home" -> {
                    item {
                        SpacePageHeader(
                            title = "Home",
                            subtitle = "Turn rows on or off, and move them.",
                            onBack = { page = "hub" },
                        )
                    }
                    item { HomeLayoutSettings(homeLayout) }
                }
                "sources" -> {
                    item {
                        SpacePageHeader(
                            title = "Sources",
                            subtitle = sourcesPageSubtitle(
                                resolvers
                                    .filter { it.server.id in catalog.bundledIds }
                                    .map { it.server.name },
                            ),
                            onBack = { page = "hub" },
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = onOpenStore,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.accent,
                                    contentColor = palette.onAccent,
                                ),
                            ) {
                                Text("Store", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    catalog.extensions.enableAll()
                                    extensionStatus = "All live sites and extensions enabled"
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, palette.hairline),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.text),
                            ) {
                                Text("Enable all", fontWeight = FontWeight.Bold)
                            }
                        }
                        if (extensionStatus != null) {
                            Text(
                                extensionStatus!!,
                                color = palette.text.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                    }
                    lazyItems(sourceRows, key = { it.id }) { server ->
                        val selected = server.id == selectedId
                        val isExtension = installedExt.any { it.id == server.id }
                        val isBundled = server.id in catalog.bundledSiteIds
                        val isEnabled = catalog.extensions.isEnabled(server.id)
                        val cold = server.id.lowercase() in coldKeys ||
                            server.name.trim().lowercase() in coldKeys
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .fillMaxWidth()
                                .stageGlassPlate(shape = RoundedCornerShape(16.dp), elevated = selected && isEnabled)
                                .then(
                                    if (isEnabled) {
                                        Modifier.spacePress { catalog.select(server.id) }
                                    } else {
                                        Modifier
                                    },
                                )
                                .semantics {
                                    contentDescription = sourceRowDescription(server.name, isExtension, selected)
                                }
                                .then(
                                    if (selected && isEnabled) {
                                        Modifier.border(0.5.dp, palette.accent, RoundedCornerShape(16.dp))
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(14.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ExtensionIcon(
                                    iconUrl = server.iconUrl,
                                    contentDescription = null,
                                    size = 40.dp,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            server.name,
                                            color = palette.text,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f, fill = false).clearAndSetSemantics { },
                                        )
                                        when {
                                            selected && isEnabled -> {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "ACTIVE",
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(999.dp))
                                                        .background(palette.accent)
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                        .clearAndSetSemantics { },
                                                    color = palette.onAccent,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                )
                                            }
                                            cold && isEnabled -> {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "COLD",
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(999.dp))
                                                        .background(palette.text.copy(alpha = 0.1f))
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                        .clearAndSetSemantics { },
                                                    color = palette.faint,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                )
                                            }
                                            !isEnabled -> {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "DISABLED",
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(999.dp))
                                                        .background(palette.text.copy(alpha = 0.1f))
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                        .clearAndSetSemantics { },
                                                    color = palette.faint,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        when {
                                            isBundled && cold -> "Live site · cooled after a hard fail"
                                            isBundled -> "Live site · in the race"
                                            isExtension -> "Extension"
                                            else -> "Source"
                                        },
                                        color = palette.faint,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.clearAndSetSemantics { },
                                    )
                                }
                                if (isBundled || isExtension) {
                                    Switch(
                                        checked = isEnabled,
                                        onCheckedChange = { enabled ->
                                            catalog.extensions.setEnabled(server.id, enabled)
                                            extensionStatus = if (enabled) {
                                                "${server.name} is back in the failover chain"
                                            } else {
                                                "${server.name} won't be used until you enable it again"
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = KittyOnAccent,
                                            checkedTrackColor = KittyAccent,
                                            uncheckedThumbColor = Color.White,
                                            uncheckedTrackColor = Color.White.copy(alpha = 0.24f),
                                            uncheckedBorderColor = Color.White.copy(alpha = 0.35f),
                                        ),
                                    )
                                }
                            }
                            Text(
                                server.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.muted,
                                modifier = Modifier.padding(top = 8.dp).clearAndSetSemantics { },
                            )
                            if (isExtension) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = {
                                        catalog.uninstallExtension(server.id)
                                        extensionStatus = "Removed ${server.name}"
                                    },
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text("Uninstall", color = palette.muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                "player" -> {
                    item {
                        SpacePageHeader(
                            title = "Player",
                            subtitle = "Defaults the video player uses. Changes apply the next time you play.",
                            onBack = { page = "hub" },
                        )
                    }
                    item { PlayerSettingsCard() }
                }
                "anifuse" -> {
                    item {
                        SpacePageHeader(
                            title = "AniFuse API (optional)",
                            subtitle = "Playback uses live site extensions on this phone. This hosted API is optional for self-hosters only.",
                            onBack = { page = "hub" },
                        )
                    }
                    item {
                        AnifuseApiSettingsCard(
                            api = anifuseApi,
                            store = anifuseStore,
                            onOpenProviders = onOpenProviders,
                        )
                    }
                }
                else -> {
                    item {
                        SpacePageHeader(
                            title = "App",
                            subtitle = "Version ${BuildConfig.VERSION_NAME}",
                            onBack = { page = "hub" },
                        )
                    }
                    item { AppBuildsRow(onClick = onOpenBuilds) }
                    item {
                        LookSettings(
                            onExport = onExportProgress,
                            onPickImport = {
                                importPicker.launch(
                                    arrayOf("application/json", "text/plain", "application/octet-stream"),
                                )
                            },
                            importNote = importNote,
                        )
                    }
                    item {
                        SpaceCard {
                            Text(
                                "About AniFuse",
                                color = palette.text,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Text(
                                "AniFuse — Discover. Stream. Enjoy.",
                                color = palette.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            Text(
                                "AniFuse is a client and player. It does not host anime video and is not " +
                                    "affiliated with AniList, any studio, or the sites its resolver fronts. " +
                                    "A stream plays only when a third-party site serves it; availability is " +
                                    "best-effort and the sites control those streams. You are responsible for " +
                                    "the laws where you are and for the terms of every site it queries. " +
                                    "Apache License 2.0.",
                                color = palette.muted,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    item { ImageCacheCard() }
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                "Copyright 2026 fampep. Apache License 2.0.",
                                color = palette.text.copy(alpha = 0.45f),
                                fontSize = 13.sp,
                            )
                            TmdbCredit()
                        }
                    }
                }
            }
        }
        }
        }
    }
}

@Composable
internal fun TmdbCredit() {
    val palette = LocalKittyPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = androidx.compose.ui.res.painterResource(site.anikitty.app.R.drawable.tmdb_logo),
            contentDescription = "The Movie Database",
            modifier = Modifier
                .height(16.dp)
                .width(22.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "This product uses the TMDB API but is not endorsed or certified by TMDB.",
            color = palette.muted,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SettingsHubBody(
    wide: Boolean,
    demo: Boolean,
    accountDetail: String,
    avatarUrl: String?,
    homeShown: Int,
    sourceName: String,
    apiEndpoint: String,
    onOpen: (String) -> Unit,
    onOpenInbox: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
) {
    val appearance = @Composable {
        SettingsGroup(title = "Appearance") {
            SettingsHubRow(
                icon = FluentuiSystemIconsSettingsOutline,
                title = "Black & white",
                detail = "AniFuse theme · v${BuildConfig.VERSION_NAME}",
                plain = true,
                grabFocus = wide,
                onClick = { onOpen("app") },
            )
            SettingsHubRow(
                icon = FluentuiSystemIconsHomeOutline,
                title = "Home layout",
                detail = "$homeShown rows showing",
                plain = true,
                onClick = { onOpen("home") },
            )
        }
    }
    val playback = @Composable {
        SettingsGroup(title = "Playback") {
            SettingsHubRow(
                icon = FluentuiSystemIconsPlayFilled,
                title = "Player",
                detail = "Autoplay, speed, audio",
                plain = true,
                onClick = { onOpen("player") },
            )
        }
    }
    val anilist = @Composable {
        SettingsGroup(title = "AniList") {
            SettingsHubRow(
                icon = FluentuiSystemIconsPersonOutline,
                title = "Account",
                detail = accountDetail,
                avatarUrl = avatarUrl,
                useAvatar = true,
                plain = true,
                onClick = { onOpen("account") },
            )
            SettingsHubRow(
                icon = FluentuiSystemIconsMoviesAndTvOutline,
                title = "Devices",
                detail = if (demo) "Not available in the demo" else "Pair or unpair a TV",
                plain = true,
                onClick = { onOpen("devices") },
            )
        }
    }
    val extensions = @Composable {
        SettingsGroup(title = "Extensions") {
            SettingsHubRow(
                icon = Icons.Outlined.Extension,
                title = "Sources & store",
                detail = sourceName,
                plain = true,
                onClick = { onOpen("sources") },
            )
            SettingsHubRow(
                icon = FluentuiSystemIconsArrowSync,
                title = "Optional API",
                detail = "Not used for playback · $apiEndpoint",
                plain = true,
                onClick = { onOpen("anifuse") },
            )
        }
    }
    val downloads = @Composable {
        SettingsGroup(title = "Downloads") {
            SettingsHubRow(
                icon = FluentuiSystemIconsBookmarkOutline,
                title = "Saved episodes",
                detail = "Play copies stored on this phone",
                plain = true,
                onClick = onOpenDownloads,
            )
        }
    }
    val notifications = @Composable {
        SettingsGroup(title = "Notifications") {
            SettingsHubRow(
                icon = FluentuiSystemIconsAlertOutline,
                title = "Notification centre",
                detail = "Airing, downloads, updates",
                plain = true,
                onClick = onOpenInbox,
            )
        }
    }
    val about = @Composable {
        SettingsGroup(title = "About") {
            SettingsHubRow(
                icon = FluentuiSystemIconsSettingsOutline,
                title = "About AniFuse",
                detail = "Discover. Stream. Enjoy.",
                plain = true,
                onClick = { onOpen("app") },
            )
        }
    }
    if (wide) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                appearance()
                playback()
                anilist()
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                extensions()
                downloads()
                notifications()
                about()
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            appearance()
            playback()
            anilist()
            extensions()
            downloads()
            notifications()
            about()
        }
    }
}

@Composable
internal fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    val palette = LocalKittyPalette.current
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            title.uppercase(),
            color = palette.muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.6.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        val shape = RoundedCornerShape(site.anikitty.app.ui.theme.KittyRadius.lg)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .stageGlassPlate(shape = shape, elevated = false),
            content = { content() },
        )
    }
}

@Composable
internal fun SettingsHubRow(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
    avatarUrl: String? = null,
    useAvatar: Boolean = false,
    plain: Boolean = false,
    grabFocus: Boolean = false,
) {
    val palette = LocalKittyPalette.current
    val row: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 2.dp),
        ) {
            if (useAvatar) {
                ProfileMark(imageUrl = avatarUrl, size = 40.dp)
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.page),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = palette.text, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = palette.text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                Text(
                    detail,
                    color = palette.muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = palette.faint,
                modifier = Modifier.size(22.dp),
            )
        }
    }
    if (plain) {
        Box(modifier = Modifier.fillMaxWidth().spacePress(onClick, grabFocus).padding(horizontal = 14.dp, vertical = 15.dp)) {
            row()
        }
    } else {
        SpaceCard(onClick = onClick) { row() }
    }
}

@Composable
private fun ThemeModeChip(
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalKittyPalette.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) Color.White else Color(0xFF101010))
            .border(1.dp, if (selected) Color.White else Color(0xFF292929), shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(
            label,
            color = if (selected) Color(0xFF080808) else palette.text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
        Text(
            detail,
            color = if (selected) Color(0xFF505050) else palette.muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

internal fun sourceRowDescription(name: String, extension: Boolean, selected: Boolean): String =
    buildString {
        append(name.trim())
        append(if (extension) ", extension" else ", bundled")
        if (selected) append(", selected")
    }

/**
 * Resolver endpoint configuration: edit the base URL, test the connection,
 * and jump to the provider health board. The live URL feeds every resolve
 * request immediately — no restart needed.
 */
@Composable
private fun AnifuseApiSettingsCard(
    api: AnifuseApi,
    store: AnifuseApiStore,
    onOpenProviders: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalKittyPalette.current
    val scope = rememberCoroutineScope()
    val savedEndpoint by store.baseUrl.collectAsState()
    var draft by remember { mutableStateOf(savedEndpoint) }
    var note by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    fun test() {
        testing = true
        note = null
        scope.launch {
            note = runCatching {
                val health: AnifuseHealth = api.health()
                if (health.ok) {
                    "Online · ${health.providers} providers · up ${uptimeShort(health.uptimeSeconds)}"
                } else {
                    "Reachable, but reports unhealthy (${health.unhealthy} providers down)"
                }
            }.getOrElse { e -> friendlyFailure(e.message) }
            testing = false
        }
    }

    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SpaceCard {
            Text(
                "Base URL",
                color = palette.text,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                "Optional. Live site extensions handle playback. Keep HTTPS if you self-host this API for experiments; plain HTTP is only for local testing.",
                color = palette.muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = palette.text,
                    unfocusedTextColor = palette.text,
                    focusedBorderColor = palette.accent,
                    unfocusedBorderColor = palette.hairline,
                    cursorColor = palette.accent,
                ),
            )
            if (draft.trim().startsWith("http://", ignoreCase = true)) {
                Text(
                    "Warning: this endpoint is not encrypted. Anyone on the network can see the requests.",
                    color = palette.accent,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        store.setBaseUrl(draft)
                        api.invalidate()
                        note = "Endpoint updated. Streams now resolve from ${store.baseUrl.value}."
                    },
                    enabled = draft.trim().isNotEmpty() && draft.trim() != savedEndpoint,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accent,
                        contentColor = palette.onAccent,
                    ),
                ) { Text("Save", fontWeight = FontWeight.Bold) }
                if (store.isCustom) {
                    OutlinedButton(
                        onClick = {
                            store.resetBaseUrl()
                            api.invalidate()
                            draft = store.baseUrl.value
                            note = "Back to the hosted demo API."
                        },
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Use demo API") }
                }
            }
        }
        SpaceCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Connection test",
                        color = palette.text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        note ?: "Checks /healthz on the saved endpoint.",
                        color = if (note != null && note!!.contains("Online")) palette.accent else palette.muted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Button(
                    onClick = { test() },
                    enabled = !testing,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.surface.copy(),
                        contentColor = palette.text,
                    ),
                ) { Text(if (testing) "Testing…" else "Test") }
            }
        }
        SpaceCard(onClick = onOpenProviders) {
            Text(
                "Provider health",
                color = palette.text,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                "Live status, success rates, and cooldowns for every site the resolver fronts.",
                color = palette.muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            "AniFuse resolves streams from third-party sites and hosts nothing itself. Availability is best-effort and the sites control the streams.",
            color = palette.faint,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

private fun uptimeShort(seconds: Long): String {
    val days = seconds / 86_400L
    val hours = (seconds % 86_400L) / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

/**
 * Player defaults, mirrored from the player's own sheets. Every value writes
 * the exact preference keys the player reads, so nothing drifts out of sync.
 */
@Composable
private fun PlayerSettingsCard() {
    val context = LocalContext.current
    val palette = LocalKittyPalette.current
    val playerPrefs = remember { context.getSharedPreferences("anikitty_player", 0) }
    val watchPrefs = remember { context.getSharedPreferences("anikitty_watch", 0) }
    var autoNext by remember { mutableStateOf(playerPrefs.getBoolean("auto_next", true)) }
    var autoSkip by remember { mutableStateOf(playerPrefs.getBoolean("auto_skip", false)) }
    var speed by remember { mutableStateOf(playerPrefs.getFloat("playback_speed", 1f).coerceIn(0.5f, 2f)) }
    var boost by remember { mutableStateOf(playerPrefs.getInt("volume_boost", 0).coerceIn(0, 3)) }
    var audio by remember {
        mutableStateOf(watchPrefs.getString("audio", "sub")?.takeIf { it == "dub" } ?: "sub")
    }

    fun persistSpeed(value: Float) {
        speed = value
        playerPrefs.edit().putFloat("playback_speed", value).apply()
    }

    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SpaceCard {
            Text(
                "Default audio",
                color = palette.text,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                "The Sub / Dub mode a title opens with. Multi-audio streams can still switch in the player.",
                color = palette.muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("sub" to "Sub", "dub" to "Dub").forEach { (value, label) ->
                    val selected = audio == value
                    Text(
                        label,
                        color = if (selected) palette.page else palette.text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) palette.text else Color.Transparent)
                            .border(
                                1.dp,
                                if (selected) palette.text else palette.hairline,
                                RoundedCornerShape(999.dp),
                            )
                            .clickable {
                                audio = value
                                watchPrefs.edit().putString("audio", value).apply()
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
        SpaceCard {
            Text(
                "Playback speed",
                color = palette.text,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                    val selected = kotlin.math.abs(speed - value) < 0.01f
                    Text(
                        speedLabel(value),
                        color = if (selected) palette.page else palette.text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) palette.text else Color.Transparent)
                            .border(
                                1.dp,
                                if (selected) palette.text else palette.hairline,
                                RoundedCornerShape(999.dp),
                            )
                            .clickable { persistSpeed(value) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
        SpaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Autoplay next episode",
                        color = palette.text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        "Starts the next episode after a short countdown.",
                        color = palette.muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = autoNext,
                    onCheckedChange = {
                        autoNext = it
                        playerPrefs.edit().putBoolean("auto_next", it).apply()
                    },
                )
            }
        }
        SpaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Skip intro and outro",
                        color = palette.text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        "Jumps past openings and credits when real timestamps exist.",
                        color = palette.muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = autoSkip,
                    onCheckedChange = {
                        autoSkip = it
                        playerPrefs.edit().putBoolean("auto_skip", it).apply()
                    },
                )
            }
        }
        SpaceCard {
            Text(
                "Volume boost · ${boostLabel(boost)}",
                color = palette.text,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                "Extra gain for quiet streams. Off by default.",
                color = palette.muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (0..3).forEach { level ->
                    val selected = boost == level
                    Text(
                        boostLabel(level),
                        color = if (selected) palette.page else palette.text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) palette.text else Color.Transparent)
                            .border(
                                1.dp,
                                if (selected) palette.text else palette.hairline,
                                RoundedCornerShape(999.dp),
                            )
                            .clickable {
                                boost = level
                                playerPrefs.edit().putInt("volume_boost", level).apply()
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

private fun boostLabel(level: Int): String = when (level) {
    1 -> "Low"
    2 -> "Medium"
    3 -> "High"
    else -> "Off"
}

/** Local poster/backdrop cache size and a one-tap clear. */
@Composable
private fun ImageCacheCard() {
    val context = LocalContext.current
    val palette = LocalKittyPalette.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    SpaceCard {
        Text(
            "Image cache",
            color = palette.text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
            "Posters and backdrops stored on this phone. Clearing it frees space; images reload as you browse.",
            color = palette.muted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!note.isNullOrBlank()) {
                Text(
                    note!!,
                    color = palette.muted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    if (busy) return@OutlinedButton
                    busy = true
                    scope.launch {
                        val cleared = withContext(Dispatchers.IO) {
                            val cache = context.imageLoader.diskCache ?: return@withContext 0L
                            val size = cache.size
                            cache.clear()
                            size
                        }
                        note = if (cleared > 0L) "Cleared ${formatBytes(cleared)}" else "Cache is empty"
                        busy = false
                    }
                },
                shape = RoundedCornerShape(12.dp),
                enabled = !busy,
            ) { Text(if (busy) "Clearing…" else "Clear cache") }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

/** AniList about fields are HTML (and often markdown) — Profile shows plain text only. */
private fun stripAniListAbout(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val plain = android.text.Html.fromHtml(raw, android.text.Html.FROM_HTML_MODE_COMPACT)
        ?.toString()
        .orEmpty()
    return plain
        .replace('\u00a0', ' ')
        .replace(Regex("`+"), "")
        .replace(Regex("\\*{1,3}"), "")
        .replace(Regex("_{1,3}"), "")
        .replace(Regex("~{1,2}"), "")
        .lines()
        .joinToString(" ") { it.trim() }
        .replace(Regex("\\s{2,}"), " ")
        .trim()
}
