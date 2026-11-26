// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import site.anikitty.app.AnikittyApp
import site.anikitty.app.BuildConfig
import site.anikitty.app.data.anilist.AniListClient
import site.anikitty.app.data.anilist.AnimeSummary
import site.anikitty.app.data.download.DownloadPhase
import site.anikitty.app.data.local.WatchHistoryEntity
import site.anikitty.app.data.updates.AppReleaseClient
import site.anikitty.app.data.updates.BuildChannelStore
import site.anikitty.app.data.updates.channelBuild
import site.anikitty.app.data.updates.isNewerAppBuild
import site.anikitty.app.data.updates.selectedBuildChannel
import site.anikitty.app.ui.theme.LocalKittyPalette
import site.anikitty.app.ui.theme.ShimmerBox
import site.anikitty.app.ui.theme.kittyPageBrush
import site.anikitty.app.ui.theme.LookState

private const val PREFS = "anifuse_inbox"
private const val KEY_READ = "read"
private const val KEY_CLEARED = "cleared"

internal data class InboxNote(
    val id: String,
    val title: String,
    val body: String,
    val anime: AnimeSummary? = null,
    val episode: Int? = null,
)

/**
 * Notes the app can actually observe: saved episodes, airings for titles
 * already in the library, and a newer build from the existing release client.
 * An empty list stays empty — nothing is fabricated.
 */
internal suspend fun loadInboxNotes(
    context: Context,
    aniList: AniListClient,
    history: List<WatchHistoryEntity>,
): List<InboxNote> = withContext(Dispatchers.IO) {
    val notes = mutableListOf<InboxNote>()
    val downloads = runCatching {
        (context.applicationContext as AnikittyApp).database.episodeDownloadDao().recent()
    }.getOrDefault(emptyList())
    downloads.forEach { row ->
        val state = when (row.phase) {
            DownloadPhase.READY.name -> "Saved on this phone"
            DownloadPhase.RUNNING.name -> "Saving · ${row.progressPercent}%"
            DownloadPhase.FAILED.name -> row.error ?: "Save failed"
            else -> "Queued"
        }
        notes += InboxNote(
            id = "dl-${row.anilistId}-${row.episode}-${row.updatedAtEpochMs}",
            title = row.title.ifBlank { "Episode ${row.episode}" },
            body = "Episode ${row.episode} · $state",
        )
    }
    val followed = history.map { it.anilistId }.toSet()
    if (followed.isNotEmpty()) {
        val now = System.currentTimeMillis() / 1000L
        val slots = runCatching {
            aniList.airingSchedule(now - 3L * 24 * 60 * 60, now + 2L * 24 * 60 * 60, newestFirst = true)
        }.getOrDefault(emptyList())
        slots.filter { it.anime.id in followed }.take(12).forEach { slot ->
            notes += InboxNote(
                id = "air-${slot.anime.id}-${slot.episode}",
                title = slot.anime.title,
                body = "Episode ${slot.episode} is on the airing schedule",
                anime = slot.anime,
                episode = slot.episode,
            )
        }
    }
    runCatching {
        val releases = AppReleaseClient().load()
        val installed = BuildConfig.VERSION_NAME
        val channel = selectedBuildChannel(BuildChannelStore(context).get(), installed)
        val remote = channelBuild(channel, releases)
        if (remote != null && isNewerAppBuild(remote.versionName, installed)) {
            notes += InboxNote(
                id = "build-${remote.versionName}",
                title = "AniFuse ${remote.versionName}",
                body = "A newer build is published. Open About to install it.",
            )
        }
    }
    notes
}

@Composable
internal fun NotificationCenter(
    aniList: AniListClient,
    history: List<WatchHistoryEntity>,
    onBack: () -> Unit,
    onOpen: (AnimeSummary, Int?) -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalKittyPalette.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var notes by remember { mutableStateOf<List<InboxNote>?>(null) }
    var read by remember { mutableStateOf(prefs.getStringSet(KEY_READ, emptySet()).orEmpty()) }
    var cleared by remember { mutableStateOf(prefs.getStringSet(KEY_CLEARED, emptySet()).orEmpty()) }

    LaunchedEffect(history.size) {
        notes = loadInboxNotes(context, aniList, history)
    }

    fun persist(nextRead: Set<String>, nextCleared: Set<String>) {
        read = nextRead
        cleared = nextCleared
        prefs.edit()
            .putStringSet(KEY_READ, nextRead)
            .putStringSet(KEY_CLEARED, nextCleared)
            .apply()
    }

    val visible = notes.orEmpty().filter { it.id !in cleared }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kittyPageBrush(LookState.look != "amoled")),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .spacePress(onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = palette.text)
            }
            Text(
                "Notifications",
                color = palette.text,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = { persist(read + visible.map { it.id }, cleared) },
                enabled = visible.any { it.id !in read },
            ) {
                Text("Mark read", color = if (visible.any { it.id !in read }) palette.text else palette.faint)
            }
            TextButton(
                onClick = { persist(read, cleared + visible.map { it.id }) },
                enabled = visible.isNotEmpty(),
            ) {
                Text("Clear", color = if (visible.isNotEmpty()) palette.text else palette.faint)
            }
        }
        when {
            notes == null -> {
                ShimmerBox(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .height(88.dp),
                )
            }
            visible.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("You're all caught up", color = palette.text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text(
                        "New episodes for shows you follow, finished downloads, and app updates show up here.",
                        color = palette.muted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visible, key = { it.id }) { note ->
                        val unread = note.id !in read
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(palette.surface)
                                .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
                                .spacePress {
                                    persist(read + note.id, cleared)
                                    note.anime?.let { onOpen(it, note.episode) }
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (unread) palette.text else palette.faint),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    note.title,
                                    color = if (unread) palette.text else palette.muted,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                )
                                Text(
                                    note.body,
                                    color = palette.muted,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
