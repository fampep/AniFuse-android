// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package site.anikitty.app.ui.player

import androidx.annotation.OptIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import site.anikitty.app.ui.components.EpisodeCarousel
import site.anikitty.app.ui.components.EpisodeSlide
import site.anikitty.app.ui.components.KittyImage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import site.anikitty.app.ui.theme.PlayerChrome
import site.anikitty.app.ui.tv.isTelevision

@Composable
internal fun PlayerPageBar(
    title: String,
    episodeLine: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val barShape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clip(barShape)
            .background(Color(0xD6101012))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.14f), barShape)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChromeDisc(onClick = onBack, size = 44.dp) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = (-0.25).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (episodeLine.isNotBlank()) {
                Text(
                    text = episodeLine,
                    color = Color.White.copy(alpha = 0.58f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun PlayerEpisodeShelf(
    title: String = "",
    episodeTitle: String? = null,
    episode: Int,
    totalEpisodes: Int,
    watchedThrough: Int,
    positionFraction: Float,
    audio: String,
    isMovie: Boolean,
    autoNext: Boolean,
    onPickEpisode: (Int) -> Unit,
    onToggleAutoNext: () -> Unit,
    episodeSlides: List<EpisodeSlide>,
    anilistId: Int = 0,
    fallbackArt: String?,
    minutesLeft: Int?,
    onToggleWatched: ((Int) -> Unit)?,
    sourceLabel: String = "Auto",
    qualityLabel: String = "Auto",
    qualitySelected: Boolean = false,
    onQuality: () -> Unit = {},
    sourceSelected: Boolean = false,
    onSource: () -> Unit = {},
    audioSelected: Boolean = false,
    onAudio: () -> Unit = {},
    onMore: () -> Unit = {},
    onEpisodes: () -> Unit = {},
    onShare: () -> Unit = {},
    shareEnabled: Boolean = false,
    downloadEnabled: Boolean = false,
    onDownload: () -> Unit = {},
    onRemoveDownload: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val showGrid = !isMovie && totalEpisodes > 1
    val audioLabel = if (audio == "dub") "Dub" else "Sub"
    val downloadSaved = onRemoveDownload != null
    val headline = episodeTitle?.takeIf { it.isNotBlank() } ?: title
    val slides = remember(episodeSlides, totalEpisodes) {
        if (episodeSlides.isNotEmpty()) {
            episodeSlides
        } else {
            (1..totalEpisodes.coerceAtLeast(1)).map { number -> EpisodeSlide(number, "Episode $number", null) }
        }
    }
    val blocks = remember(totalEpisodes) { episodeBlocks(totalEpisodes) }
    var block by remember(totalEpisodes) {
        mutableIntStateOf(
            blockIndexForEpisode(episode).coerceIn(0, (blocks.size - 1).coerceAtLeast(0)),
        )
    }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(episode, blocks.size) {
        block = blockIndexForEpisode(episode).coerceIn(0, (blocks.size - 1).coerceAtLeast(0))
    }
    val rows = remember(slides, query, block) { episodeRows(slides, query, block) }
    val listState = rememberLazyListState()
    // Items above the rows, so the current episode can be scrolled to. Skipped
    // on first paint: that would pull the title block off screen.
    val leadingItems = (if (headline.isNotBlank()) 1 else 0) + 2 + (if (showGrid) 3 else 0)
    var painted by remember { mutableStateOf(false) }
    LaunchedEffect(episode, block, query, rows.size) {
        if (!painted) {
            painted = true
            return@LaunchedEffect
        }
        // A search is about its matches, not the episode being watched.
        if (query.isBlank()) {
            val index = rows.indexOfFirst { it.number == episode }
            if (index >= 0) {
                runCatching { listState.animateScrollToItem(leadingItems + index) }
            }
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Episode title under the video, like the reference app.
        if (headline.isNotBlank()) {
            item(key = "title") {
                Column {
                    Text(
                        headline,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        letterSpacing = (-0.5).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Episode $episode · ${if (audio == "dub") "DUB" else "SUB"}",
                        color = Color.White.copy(alpha = 0.58f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        letterSpacing = 0.2.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        item(key = "values") {
            PlayerValueCard(
                sourceLabel = sourceLabel,
                qualityLabel = qualityLabel,
                qualitySelected = qualitySelected,
                onQuality = onQuality,
                sourceSelected = sourceSelected,
                onSource = onSource,
                audioLabel = audioLabel,
                audioSelected = audioSelected,
                onAudio = onAudio,
                onMore = onMore,
            )
        }
        item(key = "actions") {
            PlayerActionRow(
                onShare = onShare,
                shareEnabled = shareEnabled,
                onDownload = {
                    if (downloadSaved) onRemoveDownload?.invoke() else onDownload()
                },
                downloadEnabled = downloadSaved || downloadEnabled,
                downloadSaved = downloadSaved,
                watched = watchedThrough >= episode,
                watchedEnabled = onToggleWatched != null,
                onToggleWatched = { onToggleWatched?.invoke(episode) },
                onEpisodes = onEpisodes,
            )
        }
        // The full episode browser lives in the Episodes sheet. The shelf keeps
        // the reference app's header, search, range chip and rows so the next
        // episode is one tap away while watching.
        if (showGrid) {
            item(key = "episodes-header") {
                EpisodeSectionHeader(totalEpisodes, audio)
            }
            item(key = "episodes-controls") {
                EpisodeSectionControls(
                    query = query,
                    onQueryChange = { query = it },
                    selectedBlock = block,
                    blocks = blocks,
                    onBlockSelect = { block = it },
                )
            }
            item(key = "episodes-on") {
                OnEpisodeLine(
                    episode = episode,
                    autoNext = autoNext,
                    onToggleAutoNext = onToggleAutoNext,
                )
            }
            if (rows.isEmpty()) {
                item(key = "episodes-empty") {
                    NoEpisodeMatches(query)
                }
            }
            items(rows, key = { "episode-${it.number}" }) { slide ->
                EpisodeRowCell(
                    slide = slide,
                    current = slide.number == episode,
                    progress = if (slide.number == episode) positionFraction else 0f,
                    fallbackArt = fallbackArt,
                    onPick = { onPickEpisode(slide.number) },
                    onOptions = onToggleWatched?.let { toggle -> { toggle(slide.number) } },
                )
            }
        }
    }
}
@Composable
private fun ShelfCell(
    label: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
        )
        Text(
            value,
            color = if (selected) PlayerChrome.Accent else Color.White.copy(alpha = 0.55f),
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun ShelfDivider() {
    Box(
        Modifier
            .size(width = 0.5.dp, height = 34.dp)
            .background(Color.White.copy(alpha = 0.10f)),
    )
}

@Composable
internal fun PlayerTool(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
            )
            .then(
                if (enabled) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .clearAndSetSemantics {
                contentDescription = playerToolDescription(label, value, enabled, selected)
            }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            value,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

@Composable
internal fun PlayerMetaPill(label: String, emphasized: Boolean = false) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (emphasized) PlayerChrome.Accent else PlayerChrome.Disc)
            .border(
                1.dp,
                if (emphasized) PlayerChrome.Accent else PlayerChrome.DiscStroke,
                shape,
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            label,
            color = if (emphasized) Color.Black else PlayerChrome.Graphite,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun ChromeDisc(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    large: Boolean = false,
    accent: Boolean = false,
    focusRequester: FocusRequester? = null,
    content: @Composable () -> Unit,
) {
    val shape = CircleShape
    var focused by remember { mutableStateOf(false) }
    val grow = if (isTelevision()) 1.02f else 1.06f
    Box(
        modifier = modifier
            .size(size)
            .scale(if (focused) grow else 1f)
            .clip(shape)
            .background(
                when {
                    accent -> PlayerChrome.Accent
                    large -> Color(0xB3101012)
                    else -> Color(0x99101012)
                },
            )
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (accent) 0.18f else 0.10f),
                        Color.Transparent,
                    ),
                ),
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused || accent) Color.White else Color.White.copy(alpha = 0.16f),
                shape,
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
internal fun LiveClockText(
    player: Player?,
    scrubbing: Boolean,
    scrubValue: Float,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    var liveMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player, scrubbing) {
        val p = player ?: return@LaunchedEffect
        while (true) {
            if (!scrubbing) {
                val next = p.currentPosition.coerceAtLeast(0L)
                if (next != liveMs) liveMs = next
            }
            delay(200)
        }
    }
    val shown = if (scrubbing) (scrubValue * durationMs).toLong() else liveMs
    val remain = (durationMs - shown).coerceAtLeast(0L)
    Text(
        "${formatClockPadded(shown)}  −${formatClockPadded(remain)}",
        color = Color.White,
        fontSize = 13.sp,
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = modifier.clearAndSetSemantics {
            contentDescription = playbackClockDescription(shown, durationMs)
        },
    )
}

@Composable
internal fun PlayerCapsule(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val television = isTelevision()
    Row(
        modifier = Modifier
            .height(if (television) 42.dp else 48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.58f))
            // A scrollable row eats D-pad left/right, so the remote never reaches the other controls.
            .horizontalScroll(rememberScrollState(), enabled = !television)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * White ring while focus sits on this control.
 * Apply it on the node that receives focus. A wrapper around a text field
 * never becomes focused itself. Width stays constant. A 0dp stroke grows
 * the button when it gains focus, and disposing that node mid-click crashes
 * the player.
 */
@Composable
internal fun Modifier.playerFocusCue(shape: Shape): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.hasFocus }
        .border(
            width = 2.dp,
            color = if (focused) Color.White else Color.Transparent,
            shape = shape,
        )
}

@Composable
internal fun PillIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val box = if (isTelevision()) 40.dp else 48.dp
    val glyph = if (isTelevision()) 18.dp else 22.dp
    Box(
        modifier = Modifier
            .size(box)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .playerFocusCue(CircleShape)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(glyph),
        )
    }
}

@Composable
internal fun PillLabel(
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    val box = if (isTelevision()) 40.dp else 48.dp
    Box(
        modifier = Modifier
            .size(box)
            .playerFocusCue(CircleShape)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 13.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
        )
    }
}
