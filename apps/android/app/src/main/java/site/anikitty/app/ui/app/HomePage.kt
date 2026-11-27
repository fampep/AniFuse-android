// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import site.anikitty.app.ui.icons.FluentuiSystemIconsAlertOutline
import site.anikitty.app.ui.icons.FluentuiSystemIconsArrowSync
import site.anikitty.app.ui.icons.FluentuiSystemIconsSearchSparkleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import site.anikitty.app.data.anilist.AiringSlot
import site.anikitty.app.data.anilist.AnimeSummary
import site.anikitty.app.data.episodeCountLabel
import site.anikitty.app.data.mediaFormatLabel
import site.anikitty.app.data.watchedAgo
import site.anikitty.app.ui.components.HeroBanner
import site.anikitty.app.ui.components.StageSlider
import site.anikitty.app.ui.components.KittyImage
import site.anikitty.app.ui.components.PrismMark
import site.anikitty.app.ui.components.sharedPoster
import site.anikitty.app.ui.components.KittyBadge
import site.anikitty.app.ui.components.KittySectionHeading
import site.anikitty.app.ui.theme.KittyRadius
import site.anikitty.app.ui.theme.KittySpace
import site.anikitty.app.ui.theme.LocalDemoMode
import site.anikitty.app.ui.theme.LocalKittyPalette
import site.anikitty.app.ui.theme.kittyFloatingChrome

private val Hairline = Color.White.copy(alpha = 0.14f)

@Composable
internal fun HomeBar(
    onRefresh: () -> Unit,
    refreshBusy: Boolean,
    onOpenSearch: () -> Unit,
    onOpenAccount: () -> Unit,
    avatarUrl: String?,
    onShuffle: (() -> Unit)? = null,
    onOpenNotifications: () -> Unit = {},
) {
    val palette = LocalKittyPalette.current
    val ink = palette.text
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Brand-first top bar — Animo layout, AniFuse monochrome.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "ANIFUSE",
                color = palette.muted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 1.8.sp,
            )
            Text(
                "Tonight",
                color = ink,
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
                letterSpacing = (-1.1).sp,
                maxLines = 1,
            )
        }
        if (LocalDemoMode.current) {
            KittyBadge(
                label = "Demo",
                onDark = palette.dark,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        HomeBarIcon(onClick = onOpenSearch) {
            Icon(
                FluentuiSystemIconsSearchSparkleOutline,
                contentDescription = "Search",
                tint = ink,
                modifier = Modifier.size(24.dp),
            )
        }
        HomeBarIcon(onClick = onOpenNotifications) {
            Icon(
                FluentuiSystemIconsAlertOutline,
                contentDescription = "Notifications",
                tint = ink,
                modifier = Modifier.size(22.dp),
            )
        }
        // Compact glass capsule: shuffle + refresh live here so the bar stays clean.
        Row(
            modifier = Modifier
                .padding(start = 2.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = if (palette.dark) 0.08f else 0.92f))
                .border(1.dp, Color.White.copy(alpha = if (palette.dark) 0.16f else 0.40f), RoundedCornerShape(999.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onShuffle != null) {
                HomeBarIcon(onClick = onShuffle) {
                    Icon(
                        Icons.Outlined.Shuffle,
                        contentDescription = "Random anime",
                        tint = ink,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            HomeBarIcon(onClick = onRefresh) {
                if (refreshBusy) {
                    site.anikitty.app.ui.components.KittyFace(
                        modifier = Modifier.size(16.dp),
                        waving = true,
                        contentDescription = "Refreshing",
                    )
                } else {
                    Icon(
                        FluentuiSystemIconsArrowSync,
                        contentDescription = "Refresh",
                        tint = ink,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            ProfileMark(
                imageUrl = avatarUrl,
                size = 30.dp,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp).spacePress(onOpenAccount),
            )
        }
    }
}

@Composable
private fun HomeBarIcon(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .spacePress(onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
internal fun HomeFeatured(
    items: List<AnimeSummary>,
    onOpen: (AnimeSummary) -> Unit,
    onWatch: (AnimeSummary) -> Unit,
    onAdd: (AnimeSummary) -> Unit,
    savedIds: Set<Int> = emptySet(),
    logos: Map<Int, String> = emptyMap(),
    inset: Boolean = true,
    onViewAll: (() -> Unit)? = null,
    heading: String = "Today released",
) {
    val pages = items.take(8)
    if (pages.isEmpty()) return
    val context = LocalContext.current
    val heroAutoplay = context.getSharedPreferences("anikitty_home", android.content.Context.MODE_PRIVATE)
        .getBoolean(HomeLayoutStore.KEY_HERO_AUTOPLAY, true)
    val stageShape = RoundedCornerShape(if (inset) 28.dp else 0.dp)
    StageSlider(
        pageCount = pages.size,
        autoAdvanceMillis = if (heroAutoplay) 5_200L else 0L,
        showIndex = pages.size > 1,
        indexInset = 118.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (inset) 16.dp else 0.dp,
                top = if (inset) 8.dp else 0.dp,
                end = if (inset) 16.dp else 0.dp,
            )
            .height(if (inset) 460.dp else 438.dp)
            .clip(stageShape),
    ) { page ->
        val anime = pages[page]
        val saved = anime.id in savedIds
        val logo = logos[anime.id]
        Box(modifier = Modifier.fillMaxSize().spacePress { onWatch(anime) }) {
            HeroBanner(
                anilistId = anime.id,
                bannerUrl = anime.bannerImageUrl,
                coverUrl = anime.coverImageUrl,
                contentDescription = null,
                title = anime.title,
                year = anime.seasonYear,
                movie = anime.format.equals("MOVIE", true),
                alignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.18f),
                            0.28f to Color.Transparent,
                            0.55f to Color.Transparent,
                            0.78f to Color.Black.copy(alpha = 0.55f),
                            1f to Color(0xFF080808),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .spacePress { onWatch(anime) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Watch Now",
                    tint = Color(0xFF080808),
                    modifier = Modifier.size(28.dp),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC101010))
                    .border(1.dp, Color(0xFF292929), CircleShape)
                    .spacePress { onAdd(anime) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (saved) Icons.Rounded.Check else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (saved) "On Watchlist" else "Add to Watchlist",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, end = 16.dp, bottom = 22.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xCC101010))
                    .border(1.dp, Color(0xFF292929), RoundedCornerShape(22.dp))
                    .spacePress { onOpen(anime) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        heading.uppercase(),
                        color = Color(0xFFA1A1A1),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 1.4.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (onViewAll != null) {
                        Text(
                            "View all",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier.spacePress(onViewAll),
                        )
                    }
                }
                if (!logo.isNullOrBlank()) {
                    AsyncImage(
                        model = logo,
                        contentDescription = anime.title,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .height(42.dp)
                            .fillMaxWidth(0.72f),
                    )
                } else {
                    Text(
                        anime.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        letterSpacing = (-0.6).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    heroCardMeta(anime),
                    color = Color(0xFFA1A1A1),
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Episode / season line on Animo-style hero cards. */
private fun heroCardMeta(anime: AnimeSummary): String {
    val ep = anime.nextAiring?.episode?.takeIf { it > 0 }?.let { "Ep $it" }
        ?: anime.episodes?.takeIf { it > 0 }?.let { "$it eps" }
    val format = anime.format?.takeIf { it.isNotBlank() }?.let { mediaFormatLabel(it) }
    val year = anime.seasonYear?.toString()
    return listOfNotNull(format, ep, year).joinToString(" · ").ifBlank { heroMeta(anime) }
}

/** Instagram-style circular reels strip — monochrome ring, colour in the art. */
@Composable
internal fun HomeTrendingReels(
    items: List<AnimeSummary>,
    onOpen: (AnimeSummary) -> Unit,
    onViewAll: (() -> Unit)? = null,
) {
    if (items.isEmpty()) return
    val palette = LocalKittyPalette.current
    val ring = listOf(items.take(12)).first()
    Column(modifier = Modifier.fillMaxWidth().padding(top = KittySpace.md)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Trending reels",
                color = palette.text,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = (-0.3).sp,
                modifier = Modifier.weight(1f),
            )
            if (onViewAll != null) {
                Text(
                    "View all",
                    color = palette.text.copy(alpha = 0.72f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 40.dp)
                        .spacePress(onViewAll)
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(ring, key = { it.id }) { anime ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(78.dp)
                        .spacePress { onOpen(anime) },
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color.White.copy(alpha = 0.88f), CircleShape)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF141416)),
                        contentAlignment = Alignment.Center,
                    ) {
                        KittyImage(
                            url = anime.coverImageUrl,
                            contentDescription = null,
                            widthPx = 220,
                            heightPx = 220,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Text(
                        anime.title,
                        color = palette.text,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Small caps line on the hero, like the reference badge. */
private fun heroBadge(anime: AnimeSummary): String {
    val format = anime.format?.takeIf { it.isNotBlank() }?.let { raw ->
        when (raw.uppercase()) {
            "TV_SHORT" -> "TV SHORT"
            "ONA" -> "ONA"
            else -> raw.uppercase()
        }
    }
    val state = when {
        anime.status.equals("RELEASING", ignoreCase = true) -> "ONGOING"
        anime.status.equals("FINISHED", ignoreCase = true) -> "FINISHED"
        else -> null
    }
    return listOfNotNull("SPOTLIGHT", format, state).joinToString(" \u2022 ")
}

private val synopsisBreak = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val synopsisTags = Regex("<[^>]+>")
private val synopsisSpace = Regex("\\s+")

/** Short plain-text blurb for hero cards. AniList descriptions arrive as HTML. */
internal fun plainSynopsis(raw: String?): String? {
    val text = raw
        ?.replace(synopsisBreak, " ")
        ?.replace(synopsisTags, " ")
        ?.replace("&quot;", "\"")
        ?.replace("&#039;", "'")
        ?.replace("&apos;", "'")
        ?.replace("&amp;", "&")
        ?.replace("&lt;", "<")
        ?.replace("&gt;", ">")
        ?.replace(synopsisSpace, " ")
        ?.trim()
    return text?.takeIf { it.isNotEmpty() }
}

/** Year, episode count and two genres, dot separated, like the reference. */
private fun heroMeta(anime: AnimeSummary): String {
    val episodes = anime.episodes?.takeIf { it > 0 }?.let { "$it Eps" }
    val year = anime.seasonYear?.toString()
    return listOfNotNull(year, episodes)
        .plus(anime.genres.take(2))
        .joinToString(" \u2022 ")
}

@Composable
internal fun HomeBlock(
    title: String,
    subtitle: String? = null,
    onMore: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(top = KittySpace.lg)) {
        KittySectionHeading(title = title, subtitle = subtitle, onMore = onMore)
        Spacer(modifier = Modifier.height(KittySpace.sm))
        content()
    }
}

internal fun posterColumns(maxWidth: androidx.compose.ui.unit.Dp): Int = when {
    maxWidth >= 1000.dp -> 6
    maxWidth >= 840.dp -> 5
    maxWidth >= 600.dp -> 4
    else -> 2
}

@Composable
internal fun HomePosterLine(
    row: List<AnimeSummary>,
    columns: Int,
    onOpen: (AnimeSummary) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        row.forEach { anime ->
            HomePosterCell(
                anime = anime,
                onOpen = onOpen,
                modifier = Modifier.weight(1f),
            )
        }
        repeat(columns - row.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun HomePosterGrid(
    items: List<AnimeSummary>,
    onOpen: (AnimeSummary) -> Unit,
) {
    if (items.isEmpty()) return
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = posterColumns(maxWidth)
        Column(
            modifier = Modifier.padding(horizontal = 0.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items.chunked(columns).forEach { row ->
                HomePosterLine(row = row, columns = columns, onOpen = onOpen)
            }
        }
    }
}

@Composable
internal fun HomePosterRow(
    items: List<AnimeSummary>,
    onOpen: (AnimeSummary) -> Unit,
    captions: Map<Int, String> = emptyMap(),
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.id }, contentType = { "poster" }) { anime ->
            HomePosterCell(
                anime = anime,
                onOpen = onOpen,
                caption = captions[anime.id],
                modifier = Modifier.width(112.dp),
            )
        }
    }
}

@Composable
private fun HomePosterCell(
    anime: AnimeSummary,
    onOpen: (AnimeSummary) -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    val palette = LocalKittyPalette.current
    Column(modifier = modifier.spacePress { onOpen(anime) }) {
        KittyImage(
            url = anime.coverImageUrl,
            contentDescription = null,
            widthPx = 360,
            heightPx = 540,
            modifier = Modifier
                .sharedPoster(anime.id)
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp)),
        )
        Text(
            anime.title,
            color = palette.text,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        val meta = caption ?: listOfNotNull(
            anime.airedLabel(),
            anime.format?.takeIf { it.isNotBlank() }?.let(::mediaFormatLabel),
        ).joinToString(" · ")
        if (meta.isNotEmpty() || anime.status.equals("RELEASING", true)) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        color = palette.muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (anime.status.equals("RELEASING", true)) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .height(5.dp)
                            .width(5.dp)
                            .clip(CircleShape)
                            .background(palette.text),
                    )
                }
            }
        }
    }
}

internal data class HomeContinueCard(
    val id: Int,
    val title: String,
    val imageUrl: String?,
    val label: String,
    val progress: Float,
    val onClick: () -> Unit,
)

@Composable
internal fun HomeContinueRow(cards: List<HomeContinueCard>) {
    if (cards.isEmpty()) return
    val palette = LocalKittyPalette.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = KittySpace.md),
        horizontalArrangement = Arrangement.spacedBy(KittySpace.sm),
    ) {
        items(cards, key = { it.id }, contentType = { "still" }) { card ->
            Column(
                modifier = Modifier
                    .width(248.dp)
                    .spacePress(card.onClick),
            ) {
                ContinueArt(card)
                Text(
                    card.label,
                    color = palette.muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = KittySpace.sm),
                )
            }
        }
    }
}

@Composable
private fun ContinueArt(card: HomeContinueCard) {
    val shape = RoundedCornerShape(KittyRadius.md)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(Color(0xFF141416)),
    ) {
        KittyImage(
            url = card.imageUrl,
            contentDescription = null,
            widthPx = 640,
            heightPx = 360,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize().sharedPoster(card.id),
        )
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
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, Color.White.copy(alpha = 0.9f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = "Resume",
                tint = Color.Black,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            card.title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = (-0.15).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 12.dp, bottom = 16.dp),
        )
        if (card.progress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .background(Color.White.copy(alpha = 0.18f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(card.progress.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                        .background(Color.White),
                )
            }
        }
    }
}

internal data class HomeAiringCard(
    val key: String,
    val title: String,
    val imageUrl: String?,
    val whenLabel: String,
    val episodeLabel: String,
    val onClick: () -> Unit,
)

@Composable
internal fun HomeAiringRow(cards: List<HomeAiringCard>) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cards.forEach { card ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .spacePress(card.onClick)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0F0F13)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KittyImage(
                    url = card.imageUrl,
                    contentDescription = null,
                    widthPx = 140,
                    heightPx = 200,
                    modifier = Modifier
                        .width(54.dp)
                        .fillMaxHeight(),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        card.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${card.whenLabel}  ·  ${card.episodeLabel}",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeGenreRow(
    names: List<String>,
    onOpen: (String) -> Unit,
) {
    val palette = LocalKittyPalette.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(names, key = { it }, contentType = { "genre" }) { name ->
            Text(
                name,
                color = palette.text,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
                    .spacePress { onOpen(name) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
internal fun HomeRankRow(
    items: List<AnimeSummary>,
    onOpen: (AnimeSummary) -> Unit,
) {
    val shown = items.take(10)
    if (shown.isEmpty()) return
    val palette = LocalKittyPalette.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(shown.size, key = { shown[it].id }, contentType = { "rank" }) { index ->
            val anime = shown[index]
            Column(
                modifier = Modifier
                    .width(112.dp)
                    .spacePress { onOpen(anime) },
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    KittyImage(
                        url = anime.coverImageUrl,
                        contentDescription = null,
                        widthPx = 360,
                        heightPx = 540,
                        modifier = Modifier
                            .padding(start = 20.dp)
                            .sharedPoster(anime.id)
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Text(
                        "${index + 1}",
                        color = palette.text,
                        fontWeight = FontWeight.Black,
                        fontSize = 48.sp,
                        letterSpacing = (-2.0).sp,
                        style = TextStyle(
                            shadow = Shadow(Color.Black.copy(alpha = 0.85f), Offset(0f, 2f), 10f),
                        ),
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                }
                Text(
                    anime.title,
                    color = palette.text,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 20.dp, top = 6.dp),
                )
            }
        }
    }
}

private val LiveGreen = Color.White

@Composable
internal fun HomeLatestEpisodes(
    slots: List<AiringSlot>,
    onOpen: (AiringSlot) -> Unit,
    inset: Boolean = true,
    /** When set, Sub / Dub switches between true scrape-site lists. */
    dubSlots: List<AiringSlot> = emptyList(),
) {
    val palette = LocalKittyPalette.current
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("anikitty_watch", android.content.Context.MODE_PRIVATE)
    }
    var audioPref by remember {
        mutableStateOf(prefs.getString("audio", "sub")?.takeIf { it == "dub" } ?: "sub")
    }
    var page by rememberSaveable { mutableIntStateOf(0) }
    // An empty Dub list only changes this row. The player preference stays put.
    val audio = if (audioPref == "dub" && dubSlots.isEmpty()) "sub" else audioPref
    // Sub and Dub are separate scrape lists — never fall back across audio.
    val active = if (audio == "dub") dubSlots else slots
    if (slots.isEmpty() && dubSlots.isEmpty()) return
    // Reset paging when the audio list changes length.
    LaunchedEffect(audio, active.size) { page = 0 }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (inset) 16.dp else 0.dp)
            .padding(top = 22.dp),
    ) {
        val columns = when {
            maxWidth >= 900.dp -> 5
            maxWidth >= 680.dp -> 4
            else -> 3
        }
        val pageSize = columns * 2
        val paged = active.take(pageSize * 4)
        val pageCount = ((paged.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        val safePage = page.coerceIn(0, pageCount - 1)
        val shown = paged.drop(safePage * pageSize).take(pageSize)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Recently Updated",
                    color = palette.text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                HomeAudioChip(label = "Sub", selected = audio == "sub") {
                    audioPref = "sub"
                    prefs.edit().putString("audio", "sub").apply()
                }
                Spacer(modifier = Modifier.width(6.dp))
                HomeAudioChip(
                    label = "Dub",
                    selected = audio == "dub",
                    enabled = dubSlots.isNotEmpty(),
                ) {
                    audioPref = "dub"
                    prefs.edit().putString("audio", "dub").apply()
                }
                if (pageCount > 1) {
                    Spacer(modifier = Modifier.width(4.dp))
                    HomePagerButton(
                        icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        description = "Previous page",
                        enabled = safePage > 0,
                        onClick = { page = safePage - 1 },
                    )
                    Text(
                        "${safePage + 1} / $pageCount",
                        color = palette.muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                    HomePagerButton(
                        icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        description = "Next page",
                        enabled = safePage < pageCount - 1,
                        onClick = { page = safePage + 1 },
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (shown.isEmpty()) {
                Text(
                    if (audio == "dub") "No dubbed episodes yet."
                    else "No recent subbed episodes yet.",
                    color = palette.muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    shown.chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { slot ->
                                HomeLatestCell(
                                    slot = slot.forAudio(audio),
                                    audio = audio,
                                    onOpen = onOpen,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(columns - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun AiringSlot.forAudio(audio: String): AiringSlot {
    val dub = audio.equals("dub", ignoreCase = true)
    val preferred = if (dub) dubEpisodes else subEpisodes
    return if (preferred != null && preferred > 0 && preferred != episode) {
        copy(episode = preferred)
    } else {
        this
    }
}

@Composable
private fun HomeAudioChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = LocalKittyPalette.current
    Text(
        label,
        color = when {
            selected -> palette.page
            enabled -> palette.text
            else -> palette.faint
        },
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) palette.text else Color.Transparent)
            .border(1.dp, if (selected) palette.text else Hairline, RoundedCornerShape(14.dp))
            .then(if (enabled) Modifier.spacePress(onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun HomePagerButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalKittyPalette.current
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(if (enabled) Modifier.spacePress(onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) palette.text else palette.faint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun HomeLatestCell(
    slot: AiringSlot,
    onOpen: (AiringSlot) -> Unit,
    modifier: Modifier = Modifier,
    audio: String = "sub",
) {
    val palette = LocalKittyPalette.current
    val anime = slot.anime
    val ago = watchedAgo(slot.airingAtEpochSec * 1000L)
    val dub = audio.equals("dub", true)
    Column(
        modifier = modifier
            .spacePress { onOpen(slot) }
            .semantics {
                contentDescription = latestEpisodeDescription(anime.title, slot.episode, dub, ago)
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            KittyImage(
                url = anime.coverImageUrl,
                contentDescription = null,
                widthPx = 360,
                heightPx = 540,
                modifier = Modifier.fillMaxSize().sharedPoster(anime.id),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.82f),
                        ),
                    )
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "EP ${slot.episode}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics { },
                )
                if (ago.isNotBlank()) {
                    Text(
                        ago,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .weight(1f)
                            .clearAndSetSemantics { },
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }
        }
        val meta = listOfNotNull(
            anime.episodes?.takeIf { it > 0 }?.let(::episodeCountLabel),
            anime.format?.takeIf { it.isNotBlank() }?.let(::homeFormatShort),
            anime.seasonYear?.toString(),
        ).joinToString("  ")
        if (meta.isNotEmpty()) {
            Text(
                meta,
                color = palette.muted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp).clearAndSetSemantics { },
            )
        }
        Text(
            anime.title,
            color = palette.text,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp).clearAndSetSemantics { },
        )
    }
}

internal data class HomeRailEntry(
    val id: Int,
    val title: String,
    val imageUrl: String?,
    val meta: String,
    val live: Boolean,
    val onClick: () -> Unit,
)

@Composable
internal fun HomeRail(
    title: String,
    entries: List<HomeRailEntry>,
    inset: Boolean = true,
) {
    if (entries.isEmpty()) return
    val palette = LocalKittyPalette.current
    var expanded by rememberSaveable(title) { mutableStateOf(true) }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .padding(horizontal = if (inset) 16.dp else 0.dp)
            .padding(top = if (inset) 18.dp else 0.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.hairline, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .spacePress { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title.uppercase(),
                color = palette.muted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.6.sp,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = palette.muted,
                modifier = Modifier.size(20.dp),
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= 560.dp) 2 else 1
                val shown = entries.take(if (columns == 2) 8 else 6)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    shown.chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { entry ->
                                HomeRailEntryRow(entry, Modifier.weight(1f))
                            }
                            repeat(columns - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeRailEntryRow(entry: HomeRailEntry, modifier: Modifier = Modifier) {
    val palette = LocalKittyPalette.current
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .spacePress(entry.onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KittyImage(
            url = entry.imageUrl,
            contentDescription = null,
            widthPx = 120,
            heightPx = 168,
            modifier = Modifier
                .width(42.dp)
                .height(58.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.live) {
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(LiveGreen),
                    )
                }
                Text(
                    entry.title,
                    color = palette.text,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (entry.meta.isNotEmpty()) {
                Text(
                    entry.meta,
                    color = palette.muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
internal fun HomeMiruroStage(
    hero: List<AnimeSummary>,
    showHero: Boolean,
    latest: List<AiringSlot>,
    showLatest: Boolean,
    airing: List<HomeRailEntry>,
    showAiring: Boolean,
    upcoming: List<HomeRailEntry>,
    showUpcoming: Boolean,
    logos: Map<Int, String>,
    savedIds: Set<Int>,
    onOpen: (AnimeSummary) -> Unit,
    onWatch: (AnimeSummary) -> Unit,
    onAdd: (AnimeSummary) -> Unit,
    onLatest: (AiringSlot) -> Unit,
    latestDub: List<AiringSlot> = emptyList(),
) {
    val hasLatest = latest.isNotEmpty() || latestDub.isNotEmpty()
    val showLeft = (showHero && hero.isNotEmpty()) || (showLatest && hasLatest)
    val showRight = (showAiring && airing.isNotEmpty()) || (showUpcoming && upcoming.isNotEmpty())
    if (!showLeft && !showRight) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showLeft) {
            Column(modifier = Modifier.weight(1f)) {
                if (showHero && hero.isNotEmpty()) {
                    HomeFeatured(
                        items = hero,
                        onOpen = onOpen,
                        onWatch = onWatch,
                        onAdd = onAdd,
                        savedIds = savedIds,
                        logos = logos,
                        inset = false,
                    )
                }
                if (showLatest && hasLatest) {
                    HomeLatestEpisodes(
                        slots = latest,
                        dubSlots = latestDub,
                        onOpen = onLatest,
                        inset = false,
                    )
                }
            }
        }
        if (showRight) {
            Column(
                modifier = if (showLeft) Modifier.width(300.dp) else Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showAiring) {
                    HomeRail(title = "Top airing", entries = airing, inset = false)
                }
                if (showUpcoming) {
                    HomeRail(title = "Upcoming", entries = upcoming, inset = false)
                }
            }
        }
    }
}

/** Home shelves copied from the kotoplay chip bar, using the AniList fields we already have. */
internal enum class HomeShelf(val label: String) {
    All("All"),
    Movies("Movies"),
    Airing("Ongoing"),
    Finished("Finished"),
}

internal enum class HomeTopWindow(val label: String) {
    Season("This season"),
    Trending("Trending"),
}

internal fun AnimeSummary.onShelf(shelf: HomeShelf): Boolean = when (shelf) {
    HomeShelf.All -> true
    HomeShelf.Movies -> format.equals("MOVIE", ignoreCase = true)
    HomeShelf.Airing -> status.equals("RELEASING", ignoreCase = true)
    HomeShelf.Finished -> status.equals("FINISHED", ignoreCase = true)
}

/** Aired slots follow the same shelf as posters. A future airing does not count. */
internal fun AiringSlot.onHomeShelf(shelf: HomeShelf, nowSec: Long): Boolean {
    val aired = airingAtEpochSec <= 0L || airingAtEpochSec <= nowSec
    return aired && anime.onShelf(shelf)
}

@Composable
internal fun HomeShelfChips(
    selected: HomeShelf,
    onSelect: (HomeShelf) -> Unit,
) {
    val palette = LocalKittyPalette.current
    // Keep clear air under the pill bar and above the hero so chips never sit on the art.
    LazyRow(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(HomeShelf.entries, key = { it.name }, contentType = { "shelf" }) { shelf ->
            val on = shelf == selected
            Column(
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .spacePress { onSelect(shelf) }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    shelf.label,
                    color = if (on) palette.text else palette.muted,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .height(2.dp)
                        .width(if (on) 22.dp else 0.dp)
                        .clip(CircleShape)
                        .background(if (on) Color.White else Color.Transparent),
                )
            }
        }
    }
}

@Composable
internal fun HomeTopWindowChips(
    selected: HomeTopWindow,
    onSelect: (HomeTopWindow) -> Unit,
) {
    val palette = LocalKittyPalette.current
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HomeTopWindow.entries.forEach { window ->
            val on = window == selected
            Text(
                window.label,
                color = if (on) palette.text else palette.muted,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 13.sp,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .spacePress { onSelect(window) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}

private fun homeFormatShort(fmt: String) = mediaFormatLabel(fmt) ?: fmt

internal fun homeRailMeta(anime: AnimeSummary, extra: String? = null): String = listOfNotNull(
    anime.format?.takeIf { it.isNotBlank() }?.let(::homeFormatShort),
    extra?.takeIf { it.isNotBlank() } ?: anime.seasonYear?.toString(),
    anime.episodes?.takeIf { it > 0 }?.let(::episodeCountLabel),
    anime.scoreLabel(),
).joinToString("  ·  ")

internal fun latestEpisodeDescription(title: String, episode: Int, dub: Boolean, ago: String): String =
    buildString {
        append(title.trim())
        append(". Episode ")
        append(episode)
        append(if (dub) ". Dub" else ". Sub")
        if (ago.isNotBlank()) {
            append(". ")
            append(ago.trim())
        }
    }
