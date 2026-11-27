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
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import site.anikitty.app.data.download.EpisodeDownloadEntity
import java.util.concurrent.TimeUnit
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

private val inboxHttp: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .build()
}

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
 * Saved episodes already on the phone. The id ignores progress ticks so
 * mark-read and clear survive the next download write.
 */
internal fun downloadInboxNotes(rows: List<EpisodeDownloadEntity>): List<InboxNote> =
    rows.map { row ->
        val state = when (row.phase) {
            DownloadPhase.READY.name -> "Saved on this phone"
            DownloadPhase.RUNNING.name -> "Saving · ${row.progressPercent}%"
            DownloadPhase.FAILED.name -> row.error ?: "Save failed"
            else -> "Queued"
        }
        InboxNote(
            id = "dl-${row.anilistId}-${row.episode}",
            title = row.title.ifBlank { "Episode ${row.episode}" },
            body = "Episode ${row.episode} · $state",
        )
    }

internal suspend fun localInboxNotes(context: Context): List<InboxNote> = withContext(Dispatchers.IO) {
    val rows = runCatching {
        (context.applicationContext as AnikittyApp).database.episodeDownloadDao().recent()
    }.getOrDefault(emptyList())
    downloadInboxNotes(rows)
}

/**
 * Network notes only. Airing and the release check run together and each
 * gives up quickly, so a dead CDN or a rate limit cannot hold the list.
 */
internal suspend fun remoteInboxNotes(
    context: Context,
    aniList: AniListClient,
    history: List<WatchHistoryEntity>,
): List<InboxNote> = coroutineScope {
    val followed = history.map { it.anilistId }.toSet()
    val airing = async {
        if (followed.isEmpty()) {
            emptyList()
        } else {
            withTimeoutOrNull(6_000) {
                val now = System.currentTimeMillis() / 1000L
                runCatching {
                    aniList.airingSchedule(
                        now - 3L * 24 * 60 * 60,
                        now + 2L * 24 * 60 * 60,
                        newestFirst = true,
                    )
                }.getOrDefault(emptyList())
                    .filter { it.anime.id in followed }
                    .take(12)
                    .map { slot ->
                        InboxNote(
                            id = "air-${slot.anime.id}-${slot.episode}",
                            title = slot.anime.title,
                            body = "Episode ${slot.episode} is on the airing schedule",
                            anime = slot.anime,
                            episode = slot.episode,
                        )
                    }
            }.orEmpty()
        }
    }
    val build = async {
        withTimeoutOrNull(4_000) {
            runCatching {
                val releases = AppReleaseClient(inboxHttp).load()
                val installed = BuildConfig.VERSION_NAME
                val channel = selectedBuildChannel(BuildChannelStore(context).get(), installed)
                val remote = channelBuild(channel, releases)
                if (remote != null && isNewerAppBuild(remote.versionName, installed)) {
                    listOf(
                        InboxNote(
                            id = "build-${remote.versionName}",
                            title = "AniFuse ${remote.versionName}",
                            body = "A newer build is published. Open About to install it.",
                        ),
                    )
                } else {
                    emptyList()
                }
            }.getOrDefault(emptyList())
        }.orEmpty()
    }
    airing.await() + build.await()
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

    val historyKey = remember(history) {
        history.map { it.anilistId }.distinct().sorted().joinToString(",")
    }
    LaunchedEffect(historyKey) {
        val local = localInboxNotes(context)
        notes = local
        val remote = runCatching { remoteInboxNotes(context, aniList, history) }.getOrDefault(emptyList())
        notes = local + remote
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
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xFF101010))
                                .border(1.dp, Color(0xFF292929), RoundedCornerShape(22.dp))
                                .spacePress {
                                    persist(read + note.id, cleared)
                                    note.anime?.let { onOpen(it, note.episode) }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .width(3.dp)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (unread) Color.White else Color.Transparent),
                            )
                            val cover = note.anime?.coverImageUrl
                            if (!cover.isNullOrBlank()) {
                                AsyncImage(
                                    model = cover,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(width = 42.dp, height = 58.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                )
                            }
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
