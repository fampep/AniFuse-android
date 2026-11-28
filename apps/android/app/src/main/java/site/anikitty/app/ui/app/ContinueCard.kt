// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import site.anikitty.app.data.local.ContinueHidden
import site.anikitty.app.data.local.WatchHistoryEntity
import site.anikitty.app.data.local.continueCue
import site.anikitty.app.data.local.continueQueue
import site.anikitty.app.data.tmdb.EpisodeFrame
import site.anikitty.app.data.tmdb.TmdbCatalog
import site.anikitty.app.data.tmdb.transparentLogo
import site.anikitty.app.ui.components.KittyImage
import site.anikitty.app.ui.components.KittyMenu
import site.anikitty.app.ui.components.KittyMenuEntry
import site.anikitty.app.ui.components.StageProgressBar
import site.anikitty.app.ui.components.stageGlassPlate
import site.anikitty.app.ui.theme.KittyAtmosphere
import site.anikitty.app.ui.theme.KittyHaptics
import site.anikitty.app.ui.theme.KittyRadius
import site.anikitty.app.ui.theme.KittySpace
import site.anikitty.app.ui.theme.LocalKittyPalette
import site.anikitty.app.ui.theme.ShimmerBox
import androidx.compose.ui.platform.LocalView

private val ContinueCardWidth = 268.dp

@Composable
fun ContinueWatchRow(
    entries: List<WatchHistoryEntity>,
    onOpen: (WatchHistoryEntity, Int) -> Unit,
    onRemove: (WatchHistoryEntity) -> Unit,
    onMarkWatched: (WatchHistoryEntity, Int) -> Unit,
    onOpenTitle: (WatchHistoryEntity) -> Unit = { entry -> onOpen(entry, entry.episode) },
    releasedEpisodes: Map<Int, Int> = emptyMap(),
) {
    if (entries.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = KittySpace.md),
        horizontalArrangement = Arrangement.spacedBy(KittySpace.sm),
    ) {
        items(entries, key = { it.anilistId }, contentType = { "continue" }) { entry ->
            ContinueWatchCard(
                entry = entry,
                releasedEpisodes = releasedEpisodes[entry.anilistId],
                onOpen = { episode -> onOpen(entry, episode) },
                onOpenTitle = { onOpenTitle(entry) },
                onRemove = { onRemove(entry) },
                onMarkWatched = { episode -> onMarkWatched(entry, episode) },
            )
        }
    }
}

@Composable
fun ContinueSkeletonRow() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = KittySpace.md),
        horizontalArrangement = Arrangement.spacedBy(KittySpace.sm),
        userScrollEnabled = false,
    ) {
        items(3) {
            Column(modifier = Modifier.width(ContinueCardWidth)) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(KittyRadius.md)),
                )
                Spacer(modifier = Modifier.height(KittySpace.sm))
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(KittyRadius.sm)),
                )
                Spacer(modifier = Modifier.height(6.dp))
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.42f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(KittyRadius.sm)),
                )
            }
        }
    }
}

@Composable
fun ContinueAllPage(
    history: List<WatchHistoryEntity>,
    onBack: () -> Unit,
    onOpen: (WatchHistoryEntity, Int) -> Unit,
    onRemove: (WatchHistoryEntity) -> Unit,
    onMarkWatched: (WatchHistoryEntity, Int) -> Unit,
    onOpenTitle: (WatchHistoryEntity) -> Unit = { entry -> onOpen(entry, entry.episode) },
    releasedEpisodes: Map<Int, Int> = emptyMap(),
) {
    val palette = LocalKittyPalette.current
    val hidden = ContinueHidden.ids
    val queue = remember(history, hidden, releasedEpisodes) {
        continueQueue(history, hidden, releasedEpisodes)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        KittyAtmosphere()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
        SpacePageHeader(
            title = "Continue watching",
            subtitle = if (queue.isEmpty()) "Nothing in progress" else "Most recent first",
            onBack = onBack,
        )
        if (queue.isEmpty()) {
            Text(
                "Finished series stay off this list.",
                color = palette.muted,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = KittySpace.lg, vertical = KittySpace.md),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(
                    start = KittySpace.md,
                    end = KittySpace.md,
                    bottom = 120.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(KittySpace.sm),
                verticalArrangement = Arrangement.spacedBy(KittySpace.md),
            ) {
                items(queue, key = { it.anilistId }) { entry ->
                    ContinueWatchCard(
                        entry = entry,
                        fillWidth = true,
                        releasedEpisodes = releasedEpisodes[entry.anilistId],
                        onOpen = { episode -> onOpen(entry, episode) },
                        onOpenTitle = { onOpenTitle(entry) },
                        onRemove = { onRemove(entry) },
                        onMarkWatched = { episode -> onMarkWatched(entry, episode) },
                    )
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContinueWatchCard(
    entry: WatchHistoryEntity,
    fillWidth: Boolean = false,
    showWithoutCue: Boolean = false,
    releasedEpisodes: Int? = null,
    onOpen: (Int) -> Unit,
    onOpenTitle: () -> Unit = { onOpen(entry.episode.coerceAtLeast(1)) },
    onRemove: (() -> Unit)? = null,
    onMarkWatched: ((Int) -> Unit)? = null,
) {
    val cue = continueCue(entry, releasedEpisodes)
    if (cue == null && !showWithoutCue) return
    val shown = cue ?: libraryCue(entry)
    val palette = LocalKittyPalette.current
    val context = LocalContext.current
    val view = LocalView.current
    val shape = RoundedCornerShape(22.dp)
    var frame by remember(entry.anilistId, shown.episode) { mutableStateOf<EpisodeFrame?>(null) }
    var menu by remember(entry.anilistId) { mutableStateOf(false) }
    LaunchedEffect(entry.anilistId, shown.episode) {
        frame = withContext(Dispatchers.IO) {
            TmdbCatalog.shared(context).episodeFrame(entry.anilistId, shown.episode)
        }
    }
    val image = frame?.stillUrl ?: frame?.backdropUrl ?: entry.coverImageUrl
    val season = frame?.season
    val episodeTitle = frame?.episodeTitle?.takeIf { name ->
        name.isNotBlank() && !name.equals("Episode ${shown.episode}", ignoreCase = true)
    }
    val percent = if (shown.progress > 0f) (shown.progress * 100).toInt() else null
    val line = buildString {
        append("Episode ${shown.episode}")
        if (percent != null) {
            append(" · ")
            append("$percent%")
        }
    }
    val episodeLine = buildAnnotatedString {
        append("Episode ${shown.episode}")
        if (percent != null) {
            append(" · ")
            withStyle(androidx.compose.ui.text.SpanStyle(color = palette.accent)) {
                append("$percent%")
            }
        }
    }
    val time = when {
        shown.minutesLeft != null -> "${shown.minutesLeft} min left"
        shown.next -> "Next"
        shown.progress > 0f -> "Resume"
        else -> null
    }
    val entries = buildList {
        if (onRemove != null) add(KittyMenuEntry("Remove from row", onRemove))
        if (onMarkWatched != null) add(KittyMenuEntry("Mark watched") { onMarkWatched(shown.episode) })
        add(KittyMenuEntry("Go to title") { onOpenTitle() })
    }
    Column(
        modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(ContinueCardWidth))
            .combinedClickable(
                onClick = {
                    KittyHaptics.perform(view, KittyHaptics.Kind.Tick)
                    onOpen(shown.episode)
                },
                onLongClick = { menu = true },
            )
            .semantics {
                contentDescription = continueCardDescription(entry.title, line, time)
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .stageGlassPlate(shape = shape, elevated = true),
        ) {
            if (image.isNullOrBlank()) {
                // Poster still missing — ink plate with EP mark, not a stuck shimmer.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1A1A1E), Color(0xFF0C0C0E)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "EP ${shown.episode}",
                        color = Color.White.copy(alpha = 0.55f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        letterSpacing = 1.sp,
                    )
                }
            } else {
                KittyImage(
                    url = image,
                    contentDescription = null,
                    widthPx = 640,
                    heightPx = 360,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.78f),
                        ),
                    ),
            )
            // Episode badge — specular glass pill, white caps.
            val epShape = RoundedCornerShape(KittyRadius.sm)
            Text(
                "EP ${shown.episode}",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(KittySpace.sm)
                    .clip(epShape)
                    .background(Color(0xE6101012))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                        ),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.28f), epShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .clearAndSetSemantics { },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(KittySpace.sm)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color(0xFF080808),
                    modifier = Modifier.size(24.dp),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(48.dp)
                    .spacePress { menu = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = "More actions for ${entry.title}",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
                KittyMenu(expanded = menu, onDismiss = { menu = false }, entries = entries)
            }
            if (time != null) {
                Text(
                    time,
                    color = Color.White.copy(alpha = 0.92f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 10.dp, end = 10.dp, bottom = 12.dp)
                        .clearAndSetSemantics { },
                )
            }
            StageProgressBar(
                fraction = shown.progress,
                barHeight = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp),
            )
        }
        Spacer(modifier = Modifier.height(KittySpace.sm))
        Text(
            entry.title,
            color = palette.text,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Text(
            episodeLine,
            color = palette.muted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp).clearAndSetSemantics { },
        )
    }
}

private fun libraryCue(entry: WatchHistoryEntity): site.anikitty.app.data.local.ContinueCue {
    val progress = if (entry.durationMs > 0L) {
        (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    return site.anikitty.app.data.local.ContinueCue(
        episode = entry.episode.coerceAtLeast(1),
        next = false,
        progress = progress,
        minutesLeft = site.anikitty.app.data.minutesLeft(entry.positionMs, entry.durationMs),
    )
}

internal fun continueCardDescription(title: String, line: String, time: String?): String =
    listOf(title.trim(), line.trim(), time?.trim().orEmpty())
        .filter { it.isNotBlank() }
        .joinToString(". ")
