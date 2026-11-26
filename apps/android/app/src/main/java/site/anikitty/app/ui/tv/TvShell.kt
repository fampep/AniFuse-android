// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import site.anikitty.app.R
import site.anikitty.app.data.anilist.AiringSlot
import site.anikitty.app.data.anilist.AniListClient
import site.anikitty.app.data.anilist.AnimeSummary
import site.anikitty.app.data.demo.DemoLibrary
import site.anikitty.app.data.friendlyFailure
import site.anikitty.app.data.local.ContinueHidden
import site.anikitty.app.data.local.WatchHistoryEntity
import site.anikitty.app.data.local.continueCue
import site.anikitty.app.data.local.continueQueue
import site.anikitty.app.data.mediaFormatLabel
import site.anikitty.app.ui.app.summaryFrom
import site.anikitty.app.ui.components.KittyImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class TvDestination(
    val id: String,
    val label: String,
)

private val TvInk = Color(0xFF0A0A0A)
private val TvText = Color(0xFFEDEDED)
private val TvMuted = Color(0xFFEDEDED).copy(alpha = 0.75f)
private val TvChip = Color.White.copy(alpha = 0.14f)

/**
 * Television home. A top bar sits over a full-bleed hero, then horizontal rows.
 * Phone screens are not composed from here.
 */
@Composable
fun TvShell(
    aniList: AniListClient,
    demo: Boolean,
    history: List<WatchHistoryEntity>,
    accountLabel: String,
    signedIn: Boolean,
    onOpen: (AnimeSummary) -> Unit,
    onPlay: (AnimeSummary, Int) -> Unit,
    onResume: (WatchHistoryEntity, Int) -> Unit,
    onSignOut: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val destinations = remember {
        listOf(
            TvDestination("home", "Home"),
            TvDestination("search", "Search"),
            TvDestination("schedule", "Schedule"),
            TvDestination("library", "My Space"),
            TvDestination("settings", "Settings"),
        )
    }
    var section by rememberSaveable { mutableStateOf("home") }
    var userPicked by remember { mutableStateOf(false) }
    val homeContentFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val scheduleFocus = remember { FocusRequester() }
    val libraryFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val continueWatching = remember(history, ContinueHidden.ids) {
        continueQueue(history, ContinueHidden.ids).take(12)
    }
    val overscanH = dimensionResource(R.dimen.tv_overscan_horizontal)

    fun focusSection(id: String) {
        scope.launch {
            delay(48)
            val target = when (id) {
                "search" -> searchFocus
                "schedule" -> scheduleFocus
                "library" -> libraryFocus
                else -> homeContentFocus
            }
            awaitTvFocus(target)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) userPicked = true
                false
            },
    ) {
        TvNavRail(
            destinations = destinations,
            selectedId = section,
            onSelect = { id ->
                if (id == "settings") {
                    onOpenSettings()
                } else {
                    section = id
                    focusSection(id)
                }
            },
        )
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when (section) {
                "search" -> TvSearch(
                    aniList = aniList,
                    demo = demo,
                    fieldFocus = searchFocus,
                    inset = overscanH,
                    onOpen = onOpen,
                )
                "schedule" -> TvSchedule(
                    aniList = aniList,
                    demo = demo,
                    firstFocus = scheduleFocus,
                    inset = overscanH,
                    onPlay = onPlay,
                )
                "library" -> TvLibrary(
                    history = history,
                    continueWatching = continueWatching,
                    accountLabel = accountLabel,
                    signedIn = signedIn,
                    demo = demo,
                    firstFocus = libraryFocus,
                    inset = overscanH,
                    onOpen = onOpen,
                    onResume = onResume,
                    onSignOut = onSignOut,
                )
                else -> TvHome(
                    aniList = aniList,
                    demo = demo,
                    history = history,
                    continueWatching = continueWatching,
                    contentFocus = homeContentFocus,
                    userPicked = userPicked,
                    inset = overscanH,
                    onOpen = onOpen,
                    onPlay = onPlay,
                    onResume = onResume,
                )
            }
        }
    }
}

@Composable
private fun TvNavRail(
    destinations: List<TvDestination>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(168.dp)
            .fillMaxHeight()
            .padding(start = 18.dp, end = 10.dp, top = 22.dp, bottom = 16.dp),
    ) {
        Text(
            "ANIKITTY",
            color = TvText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.6.sp,
        )
        Spacer(Modifier.height(18.dp))
        destinations.forEach { destination ->
            TvNavCapsule(
                label = destination.label,
                selected = destination.id == selectedId,
                onClick = { onSelect(destination.id) },
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun TvNavCapsule(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val fill = when {
        focused -> Color.White
        selected -> TvChip
        else -> Color.Transparent
    }
    val ink = if (focused) Color.Black else TvText
    Text(
        label,
        color = ink,
        fontSize = 14.sp,
        fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
            .then(
                if (selected && !focused) {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                } else {
                    Modifier
                },
            )
            .tvActivate(onClick, shape = RoundedCornerShape(10.dp), grow = false)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun TvHome(
    aniList: AniListClient,
    demo: Boolean,
    history: List<WatchHistoryEntity>,
    continueWatching: List<WatchHistoryEntity>,
    contentFocus: FocusRequester,
    userPicked: Boolean,
    inset: androidx.compose.ui.unit.Dp,
    onOpen: (AnimeSummary) -> Unit,
    onPlay: (AnimeSummary, Int) -> Unit,
    onResume: (WatchHistoryEntity, Int) -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var trending by remember { mutableStateOf<List<AnimeSummary>>(emptyList()) }
    var seasonal by remember { mutableStateOf<List<AnimeSummary>>(emptyList()) }
    var popular by remember { mutableStateOf<List<AnimeSummary>>(emptyList()) }
    var airing by remember { mutableStateOf<List<AiringSlot>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    val rowGap = dimensionResource(R.dimen.tv_row_gap)
    val body = tvSp(R.dimen.tv_body)

    LaunchedEffect(demo, reload) {
        loading = true
        error = null
        if (demo) {
            val now = System.currentTimeMillis() / 1000L
            trending = DemoLibrary.featured
            seasonal = DemoLibrary.seasonal
            popular = DemoLibrary.catalog
            airing = DemoLibrary.airing(now)
            loading = false
            return@LaunchedEffect
        }
        runCatching {
            coroutineScope {
                val now = System.currentTimeMillis() / 1000L
                val trend = async { aniList.trending(perPage = 18) }
                val season = async { aniList.seasonal(perPage = 18) }
                val pop = async { aniList.popular(perPage = 18) }
                val latest = async {
                    runCatching {
                        aniList.airingSchedule(now - 36 * 60 * 60, now, newestFirst = true)
                    }.getOrDefault(emptyList())
                }
                trending = trend.await()
                seasonal = season.await()
                popular = pop.await()
                airing = latest.await().take(16)
            }
        }.onFailure {
            if (it is kotlinx.coroutines.CancellationException) throw it
            error = friendlyFailure(it.message)
        }
        loading = false
    }

    val catalog = trending + seasonal + popular
    val featuredEntry = continueWatching.firstOrNull()
    val featuredMatch = featuredEntry?.let { entry -> catalog.firstOrNull { it.id == entry.anilistId } }
    val hero = featuredMatch
        ?: featuredEntry?.let { summaryFrom(it) }
        ?: trending.firstOrNull()
        ?: seasonal.firstOrNull()
    val showRows = !loading && error == null
    LaunchedEffect(loading, hero?.id, showRows, userPicked) {
        if (loading || userPicked || (!showRows && error == null)) return@LaunchedEffect
        awaitTvFocus(contentFocus)
    }

    when {
        loading && trending.isEmpty() && hero == null -> {
            Box(Modifier.fillMaxSize().padding(start = inset, top = 28.dp)) {
                Text("Loading the catalog", color = TvMuted, fontSize = body)
            }
        }
        error != null && trending.isEmpty() && hero == null -> {
            Column(
                modifier = Modifier.padding(start = inset, top = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(error ?: "Couldn't load the catalog", color = TvText, fontSize = body)
                TvPill(label = "Retry", requester = contentFocus, onClick = { reload++ })
            }
        }
        else -> {
            val trendingRow = trending.filter { it.id != hero?.id }.ifEmpty { trending }
            val rowFocus = if (hero == null) contentFocus else null
            val focusRow = when {
                hero != null -> null
                continueWatching.isNotEmpty() -> "continue"
                airing.isNotEmpty() -> "fresh"
                trendingRow.isNotEmpty() -> "trending"
                seasonal.isNotEmpty() -> "season"
                popular.isNotEmpty() -> "popular"
                else -> null
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(rowGap),
                contentPadding = PaddingValues(bottom = 48.dp),
            ) {
                if (hero != null) {
                    item(key = "hero") {
                        val resume = history.firstOrNull { it.anilistId == hero.id }
                        val episode = resume?.let { continueCue(it)?.episode ?: it.episode.coerceAtLeast(1) }
                            ?: 1
                        TvSkylineHero(
                            anime = hero,
                            episodeLabel = resume?.let { "Episode $episode" },
                            playFocus = contentFocus,
                            onPlay = {
                                if (resume != null) onResume(resume, episode) else onPlay(hero, episode)
                            },
                        )
                    }
                }
                if (continueWatching.isNotEmpty()) {
                    item(key = "continue") {
                        TvWideRow(
                            title = "Continue watching",
                            inset = inset,
                            showPlay = true,
                            firstFocus = if (focusRow == "continue") rowFocus else null,
                            cards = continueWatching.map { entry ->
                                val episode = continueCue(entry)?.episode ?: entry.episode.coerceAtLeast(1)
                                val cue = continueCue(entry)
                                val match = catalog.firstOrNull { it.id == entry.anilistId }
                                TvCard(
                                    key = "c-${entry.anilistId}",
                                    title = entry.title,
                                    imageUrl = match?.bannerImageUrl ?: entry.coverImageUrl,
                                    subtitle = match?.cardMeta() ?: "Episode $episode",
                                    badge = "E$episode",
                                    progress = cue?.progress?.takeIf { it > 0.02f },
                                    onClick = { onResume(entry, episode) },
                                )
                            },
                        )
                    }
                }
                if (airing.isNotEmpty()) {
                    item(key = "fresh") {
                        TvWideRow(
                            title = "Latest episodes",
                            inset = inset,
                            firstFocus = if (focusRow == "fresh") rowFocus else null,
                            cards = airing.map { slot ->
                                TvCard(
                                    key = "a-${slot.anime.id}-${slot.episode}",
                                    title = slot.anime.title,
                                    imageUrl = slot.anime.bannerImageUrl ?: slot.anime.coverImageUrl,
                                    subtitle = slot.anime.cardMeta(),
                                    badge = "E${slot.episode}",
                                    onClick = { onPlay(slot.anime, slot.episode) },
                                )
                            },
                        )
                    }
                }
                if (trendingRow.isNotEmpty()) {
                    item(key = "trending") {
                        TvPosterRow(
                            title = "Trending",
                            inset = inset,
                            firstFocus = if (focusRow == "trending") rowFocus else null,
                            cards = trendingRow.map { it.toCard(onOpen) },
                        )
                    }
                }
                if (seasonal.isNotEmpty()) {
                    item(key = "season") {
                        TvPosterRow(
                            title = "This season",
                            inset = inset,
                            firstFocus = if (focusRow == "season") rowFocus else null,
                            cards = seasonal.map { it.toCard(onOpen) },
                        )
                    }
                }
                if (popular.isNotEmpty()) {
                    item(key = "popular") {
                        TvPosterRow(
                            title = "Popular",
                            inset = inset,
                            firstFocus = if (focusRow == "popular") rowFocus else null,
                            cards = popular.map { it.toCard(onOpen) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSkylineHero(
    anime: AnimeSummary,
    episodeLabel: String?,
    playFocus: FocusRequester,
    onPlay: () -> Unit,
) {
    val inset = dimensionResource(R.dimen.tv_overscan_horizontal)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.tv_hero_height)),
    ) {
        KittyImage(
            url = anime.bannerImageUrl ?: anime.coverImageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            widthPx = 1280,
            heightPx = 720,
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.72f),
                        0.42f to Color.Black.copy(alpha = 0.28f),
                        0.72f to Color.Transparent,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.28f to Color.Transparent,
                        0.62f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.48f)
                .padding(start = inset, end = 24.dp, bottom = 18.dp, top = 18.dp),
        ) {
            HeroChips(anime, episodeLabel)
            Spacer(Modifier.height(12.dp))
            Text(
                anime.title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 32.sp,
                letterSpacing = (-0.3).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val blurb = plainSynopsis(anime.description)
            if (blurb.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    blurb,
                    color = TvText.copy(alpha = 0.72f),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            TvPill(
                label = if (episodeLabel != null) "Continue" else "Play",
                icon = Icons.Rounded.PlayArrow,
                requester = playFocus,
                onClick = onPlay,
            )
        }
    }
}

@Composable
private fun HeroChips(anime: AnimeSummary, episodeLabel: String?) {
    val chips = buildList {
        mediaFormatLabel(anime.format)?.let { add(it) }
        anime.seasonYear?.let { add(it.toString()) }
        episodeLabel?.let { add(it) }
        anime.episodes?.takeIf { it > 0 }?.let { add("$it eps") }
        anime.averageScore?.takeIf { it > 0 }?.let { add("$it%") }
    }
    if (chips.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.take(4).forEach { chip ->
            Text(
                chip,
                color = TvText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(TvChip)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

private data class TvCard(
    val key: String,
    val title: String,
    val imageUrl: String?,
    val subtitle: String?,
    val badge: String? = null,
    val progress: Float? = null,
    val onClick: () -> Unit,
)

@Composable
private fun TvRowTitle(title: String, showPlay: Boolean) {
    val rowTitle = tvSp(R.dimen.tv_row_title)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showPlay) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(20.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .padding(4.dp),
            )
        }
        Text(
            title,
            color = Color.White,
            fontSize = rowTitle,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TvWideRow(
    title: String,
    cards: List<TvCard>,
    inset: androidx.compose.ui.unit.Dp,
    showPlay: Boolean = false,
    firstFocus: FocusRequester? = null,
) {
    val gap = dimensionResource(R.dimen.tv_card_gap)
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.padding(start = inset, bottom = 12.dp)) {
            TvRowTitle(title, showPlay)
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(gap),
            contentPadding = PaddingValues(start = inset, end = 48.dp, top = 6.dp, bottom = 8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(cards, key = { _, card -> card.key }) { index, card ->
                TvWideCard(card = card, requester = if (index == 0) firstFocus else null)
            }
        }
    }
}

@Composable
private fun TvWideCard(
    card: TvCard,
    requester: FocusRequester?,
) {
    val titleSize = tvSp(R.dimen.tv_card_title)
    Column(
        modifier = Modifier
            .width(216.dp)
            .tvActivate(card.onClick, requester, shape = RoundedCornerShape(12.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            KittyImage(
                url = card.imageUrl,
                contentDescription = card.title,
                modifier = Modifier.fillMaxSize(),
                widthPx = 640,
                heightPx = 360,
                contentScale = ContentScale.Crop,
            )
            if (!card.badge.isNullOrBlank()) {
                Text(
                    card.badge,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            val progress = card.progress
            if (progress != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.28f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0.04f, 1f))
                            .height(4.dp)
                            .background(Color.White),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            card.title,
            color = TvText,
            fontSize = titleSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (!card.subtitle.isNullOrBlank()) {
            Text(
                card.subtitle,
                color = TvMuted,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun TvPosterRow(
    title: String,
    cards: List<TvCard>,
    inset: androidx.compose.ui.unit.Dp,
    firstFocus: FocusRequester? = null,
) {
    val gap = dimensionResource(R.dimen.tv_card_gap)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            color = Color.White,
            fontSize = tvSp(R.dimen.tv_row_title),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = inset, bottom = 12.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(gap),
            contentPadding = PaddingValues(start = inset, end = 48.dp, top = 8.dp, bottom = 8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(cards, key = { _, card -> card.key }) { index, card ->
                TvPoster(card = card, requester = if (index == 0) firstFocus else null)
            }
        }
    }
}

@Composable
private fun TvPoster(
    card: TvCard,
    requester: FocusRequester?,
) {
    val width = dimensionResource(R.dimen.tv_card_width)
    val height = dimensionResource(R.dimen.tv_card_height)
    val titleSize = tvSp(R.dimen.tv_card_title)
    Column(
        modifier = Modifier
            .width(width)
            .tvActivate(card.onClick, requester),
    ) {
        KittyImage(
            url = card.imageUrl,
            contentDescription = card.title,
            modifier = Modifier
                .width(width)
                .height(height)
                .clip(RoundedCornerShape(10.dp)),
            widthPx = 480,
            heightPx = 720,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            card.title,
            color = TvText,
            fontSize = titleSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (!card.subtitle.isNullOrBlank()) {
            Text(
                card.subtitle,
                color = TvMuted,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun TvSearch(
    aniList: AniListClient,
    demo: Boolean,
    fieldFocus: FocusRequester,
    inset: androidx.compose.ui.unit.Dp,
    onOpen: (AnimeSummary) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AnimeSummary>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var fieldFocused by remember { mutableStateOf(false) }
    val body = tvSp(R.dimen.tv_body)
    val rowTitle = tvSp(R.dimen.tv_row_title)
    val cardWidth = dimensionResource(R.dimen.tv_card_width)
    val stroke = dimensionResource(R.dimen.tv_focus_stroke)
    LaunchedEffect(query, demo) {
        val typed = query.trim()
        if (typed.length < 2) {
            results = emptyList()
            searching = false
            error = null
            return@LaunchedEffect
        }
        delay(280)
        searching = true
        error = null
        if (demo) {
            results = DemoLibrary.catalog.filter { anime ->
                anime.searchTitles().any { it.contains(typed, ignoreCase = true) }
            }
            searching = false
            return@LaunchedEffect
        }
        runCatching { aniList.search(typed) }
            .onSuccess { results = it }
            .onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                results = emptyList()
                error = friendlyFailure(it.message)
            }
        searching = false
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = inset, end = inset, top = 22.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            textStyle = TextStyle(color = Color.White, fontSize = body, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(Color.White),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth(0.48f)
                .focusRequester(fieldFocus)
                .onFocusChanged { fieldFocused = it.isFocused }
                .clip(RoundedCornerShape(12.dp))
                .background(TvInk)
                .border(
                    width = if (fieldFocused) stroke else 1.dp,
                    color = if (fieldFocused) Color.White else Color.White.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 20.dp, vertical = 16.dp),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text("Titles, genres, specials...", color = TvMuted, fontSize = body)
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.height(18.dp))
        when {
            searching && results.isEmpty() -> Text("Searching", color = TvMuted, fontSize = body)
            error != null -> Text(error ?: "", color = Color.White, fontSize = body)
            query.trim().length >= 2 && results.isEmpty() && !searching -> {
                Text("No matches", color = TvMuted, fontSize = body)
            }
            results.isNotEmpty() -> {
                Text(
                    "Results",
                    color = Color.White,
                    fontSize = rowTitle,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(cardWidth),
                    horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tv_card_gap)),
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tv_row_gap)),
                    contentPadding = PaddingValues(bottom = 36.dp, top = 8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    gridItems(results, key = { it.id }) { anime ->
                        TvPoster(card = anime.toCard(onOpen), requester = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSchedule(
    aniList: AniListClient,
    demo: Boolean,
    firstFocus: FocusRequester,
    inset: androidx.compose.ui.unit.Dp,
    onPlay: (AnimeSummary, Int) -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var slots by remember { mutableStateOf<List<AiringSlot>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    val body = tvSp(R.dimen.tv_body)
    val rowTitle = tvSp(R.dimen.tv_row_title)
    val clock = remember { SimpleDateFormat("EEE h:mm a", Locale.getDefault()) }
    LaunchedEffect(demo, reload) {
        loading = true
        error = null
        val now = System.currentTimeMillis() / 1000L
        if (demo) {
            slots = DemoLibrary.airing(now)
            loading = false
            return@LaunchedEffect
        }
        runCatching {
            aniList.airingSchedule(now - 6 * 60 * 60, now + 36 * 60 * 60)
        }.onSuccess { slots = it.take(40) }
            .onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                error = friendlyFailure(it.message)
            }
        loading = false
    }
    val ready = !loading && (slots.isNotEmpty() || error != null)
    LaunchedEffect(ready) {
        if (ready) awaitTvFocus(firstFocus)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = inset, end = inset, top = 22.dp),
    ) {
        Text("Schedule", color = Color.White, fontSize = rowTitle, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        when {
            loading -> Text("Loading the schedule", color = TvMuted, fontSize = body)
            error != null && slots.isEmpty() -> {
                Text(error ?: "Could not load the schedule", color = Color.White, fontSize = body)
                Spacer(Modifier.height(16.dp))
                TvPill(label = "Retry", requester = firstFocus, onClick = { reload++ })
            }
            slots.isEmpty() -> Text("Nothing airing in this window.", color = TvMuted, fontSize = body)
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 36.dp, top = 8.dp),
                ) {
                    gridItemsIndexed(
                        slots,
                        key = { _, slot -> "${slot.anime.id}-${slot.episode}-${slot.airingAtEpochSec}" },
                    ) { index, slot ->
                        val whenLabel = clock.format(Date(slot.airingAtEpochSec * 1000L))
                        TvScheduleRow(
                            slot = slot,
                            whenLabel = whenLabel,
                            requester = if (index == 0) firstFocus else null,
                            onClick = { onPlay(slot.anime, slot.episode) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvScheduleRow(
    slot: AiringSlot,
    whenLabel: String,
    requester: FocusRequester?,
    onClick: () -> Unit,
) {
    val titleSize = tvSp(R.dimen.tv_card_title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TvInk)
            .tvActivate(onClick, requester, grow = false)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KittyImage(
            url = slot.anime.bannerImageUrl ?: slot.anime.coverImageUrl,
            contentDescription = null,
            modifier = Modifier
                .width(128.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp)),
            widthPx = 480,
            heightPx = 270,
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                slot.anime.title,
                color = TvText,
                fontSize = titleSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Episode ${slot.episode}  ·  $whenLabel",
                color = TvMuted,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TvLibrary(
    history: List<WatchHistoryEntity>,
    continueWatching: List<WatchHistoryEntity>,
    accountLabel: String,
    signedIn: Boolean,
    demo: Boolean,
    firstFocus: FocusRequester,
    inset: androidx.compose.ui.unit.Dp,
    onOpen: (AnimeSummary) -> Unit,
    onResume: (WatchHistoryEntity, Int) -> Unit,
    onSignOut: () -> Unit,
) {
    val planning = remember(history) {
        history.filter { it.listStatus.equals("PLANNING", ignoreCase = true) }.take(12)
    }
    val rowGap = dimensionResource(R.dimen.tv_row_gap)
    val titleSize = tvSp(R.dimen.tv_row_title)
    val body = tvSp(R.dimen.tv_body)
    val continueFirst = continueWatching.isNotEmpty()
    val planFirst = !continueFirst && planning.isNotEmpty()
    val logoutFirst = !continueFirst && !planFirst && (signedIn || demo)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(rowGap),
        contentPadding = PaddingValues(top = 22.dp, bottom = 48.dp),
    ) {
        item(key = "who") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = inset, end = inset),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("My Space", color = Color.White, fontSize = titleSize, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(accountLabel, color = TvMuted, fontSize = body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (signedIn || demo) {
                    Spacer(Modifier.width(16.dp))
                    TvPill(
                        label = if (demo) "Leave demo" else "Logout",
                        requester = if (logoutFirst) firstFocus else null,
                        onClick = onSignOut,
                    )
                }
            }
        }
        if (continueWatching.isNotEmpty()) {
            item(key = "continue") {
                TvWideRow(
                    title = "Continue watching",
                    inset = inset,
                    showPlay = true,
                    firstFocus = if (continueFirst) firstFocus else null,
                    cards = continueWatching.map { entry ->
                        val episode = continueCue(entry)?.episode ?: entry.episode.coerceAtLeast(1)
                        TvCard(
                            key = "lib-c-${entry.anilistId}",
                            title = entry.title,
                            imageUrl = entry.coverImageUrl,
                            subtitle = "Episode $episode",
                            badge = "E$episode",
                            progress = continueCue(entry)?.progress?.takeIf { it > 0.02f },
                            onClick = { onResume(entry, episode) },
                        )
                    },
                )
            }
        }
        if (planning.isNotEmpty()) {
            item(key = "plan") {
                TvPosterRow(
                    title = "Plan to watch",
                    inset = inset,
                    firstFocus = if (planFirst) firstFocus else null,
                    cards = planning.map { entry ->
                        TvCard(
                            key = "lib-p-${entry.anilistId}",
                            title = entry.title,
                            imageUrl = entry.coverImageUrl,
                            subtitle = null,
                            onClick = { onOpen(summaryFrom(entry)) },
                        )
                    },
                )
            }
        }
        if (continueWatching.isEmpty() && planning.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Nothing here yet.",
                    color = TvMuted,
                    fontSize = body,
                    modifier = Modifier.padding(start = inset),
                )
            }
        }
    }
}

private fun AnimeSummary.toCard(onOpen: (AnimeSummary) -> Unit) = TvCard(
    key = id.toString(),
    title = title,
    imageUrl = coverImageUrl,
    subtitle = cardMeta(),
    onClick = { onOpen(this) },
)

private fun AnimeSummary.cardMeta(): String? {
    val format = mediaFormatLabel(format)
    val year = seasonYear?.toString()
    val score = averageScore?.takeIf { it > 0 }?.let { "$it%" }
    return listOfNotNull(format, year, score).joinToString("  ·  ").ifBlank { null }
}

private fun plainSynopsis(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return raw
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<[^>]+>"), "")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&amp;", "&")
        .replace(Regex("~!.*?!~"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}
