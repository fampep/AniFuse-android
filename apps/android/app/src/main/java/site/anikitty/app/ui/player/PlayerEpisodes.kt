// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.anikitty.app.ui.components.EpisodeSlide
import site.anikitty.app.ui.components.KittyImage
import site.anikitty.app.ui.theme.PlayerChrome

/** Episodes per range chip, matching the reference app's 1–100 selector. */
internal const val EpisodeBlockSize = 100

/** Range chips for a season: 1–100, 101–200, … each closes on the last episode. */
internal fun episodeBlocks(total: Int): List<IntRange> {
    if (total <= 0) return emptyList()
    val count = (total + EpisodeBlockSize - 1) / EpisodeBlockSize
    return (0 until count).map { index ->
        val start = index * EpisodeBlockSize + 1
        start..minOf(total, start + EpisodeBlockSize - 1)
    }
}

/** Zero-based range chip that holds [episode]. */
internal fun blockIndexForEpisode(episode: Int): Int =
    (episode.coerceAtLeast(1) - 1) / EpisodeBlockSize

/**
 * Rows to show under the episodes header. A search looks across the whole
 * season; otherwise the selected 100-episode range is listed.
 */
internal fun episodeRows(
    slides: List<EpisodeSlide>,
    query: String,
    block: Int,
): List<EpisodeSlide> {
    val needle = query.trim()
    if (needle.isNotEmpty()) {
        val lower = needle.lowercase()
        return slides.asSequence()
            .filter { slide ->
                slide.title.lowercase().contains(lower) ||
                    slide.number.toString() == needle ||
                    "episode ${slide.number}" == lower
            }
            .take(EpisodeBlockSize)
            .toList()
    }
    val start = block.coerceAtLeast(0) * EpisodeBlockSize + 1
    val end = start + EpisodeBlockSize - 1
    return slides.filter { it.number in start..end }
}

/** "Episodes 366 total … DUB", like the reference app. */
@Composable
internal fun EpisodeSectionHeader(total: Int, audio: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            "Episodes",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 21.sp,
            letterSpacing = (-0.4).sp,
        )
        Text(
            "$total total",
            color = Color.White.copy(alpha = 0.5f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, bottom = 3.dp),
        )
        Text(
            if (audio == "dub") "DUB" else "SUB",
            color = PlayerChrome.Accent,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 0.9.sp,
            modifier = Modifier.padding(bottom = 3.dp),
        )
    }
}

/** Search field and range chip that sit between the header and the rows. */
@Composable
internal fun EpisodeSectionControls(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedBlock: Int,
    blocks: List<IntRange>,
    onBlockSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EpisodeSearchField(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.weight(1f),
        )
        EpisodeRangeChip(
            selectedBlock = selectedBlock,
            blocks = blocks,
            onSelect = onBlockSelect,
        )
    }
}

@Composable
private fun EpisodeSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(999.dp)
    val keyboard = LocalSoftwareKeyboardController.current
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TextStyle(
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        ),
        cursorBrush = SolidColor(PlayerChrome.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            "Search episodes",
                            color = Color.White.copy(alpha = 0.40f),
                            fontSize = 14.sp,
                        )
                    }
                    inner()
                }
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { onQueryChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Clear episode search",
                            tint = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun EpisodeRangeChip(
    selectedBlock: Int,
    blocks: List<IntRange>,
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    val block = blocks.getOrNull(selectedBlock)
    if (block != null) {
        Box {
            Row(
                modifier = Modifier
                    .height(48.dp)
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
                    .clickable(role = Role.Button) { open = true }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "${block.first}–${block.last}",
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = "Choose episode range",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = Color(0xFF16171C),
            ) {
                blocks.forEachIndexed { index, range ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "${range.first}–${range.last}",
                                color = if (index == selectedBlock) {
                                    PlayerChrome.Accent
                                } else {
                                    Color.White.copy(alpha = 0.85f)
                                },
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                            )
                        },
                        onClick = {
                            open = false
                            onSelect(index)
                        },
                    )
                }
            }
        }
    }
}

/** Amber dot, "You're on Episode N", and the autoplay switch on one line. */
@Composable
internal fun OnEpisodeLine(
    episode: Int,
    autoNext: Boolean,
    onToggleAutoNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(PlayerChrome.Accent),
        )
        Text(
            "You're on Episode $episode",
            color = Color.White.copy(alpha = 0.72f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        val shape = RoundedCornerShape(999.dp)
        Text(
            if (autoNext) "Auto next on" else "Auto next off",
            color = if (autoNext) PlayerChrome.Accent else Color.White.copy(alpha = 0.6f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            modifier = Modifier
                .clip(shape)
                .background(Color.White.copy(alpha = 0.05f))
                .border(
                    1.dp,
                    if (autoNext) PlayerChrome.Accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f),
                    shape,
                )
                .clickable(role = Role.Button, onClick = onToggleAutoNext)
                .clearAndSetSemantics { contentDescription = autoplayDescription(autoNext) }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** One episode in the list: gradient card, still, title, number, options. */
@Composable
internal fun EpisodeRowCell(
    slide: EpisodeSlide,
    current: Boolean,
    progress: Float,
    onPick: () -> Unit,
    onOptions: (() -> Unit)?,
    fallbackArt: String? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    val colors = if (current) {
        listOf(Color(0xFF24252C), Color(0xFF111216))
    } else {
        listOf(Color(0xFF1B1C22), Color(0xFF0B0B0E))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(colors))
            .then(
                if (current) {
                    Modifier.border(1.dp, PlayerChrome.Accent.copy(alpha = 0.8f), shape)
                } else {
                    Modifier
                },
            )
            .clickable(role = Role.Button, onClick = onPick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 92.dp, height = 55.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            val thumb = slide.imageUrl?.takeIf { it.isNotBlank() } ?: fallbackArt
            if (!thumb.isNullOrBlank()) {
                KittyImage(
                    url = thumb,
                    contentDescription = null,
                    widthPx = 184,
                    heightPx = 110,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .background(Color.Black.copy(alpha = 0.40f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                            .background(PlayerChrome.Accent),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                slide.title.ifBlank { "Episode ${slide.number}" },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "EP ${slide.number}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (current) NowPlayingPill()
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(enabled = onOptions != null, role = Role.Button) { onOptions?.invoke() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.MoreHoriz,
                contentDescription = "Episode ${slide.number} options",
                tint = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun NowPlayingPill() {
    val shape = RoundedCornerShape(999.dp)
    Text(
        "NOW PLAYING",
        color = PlayerChrome.Accent,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        modifier = Modifier
            .clip(shape)
            .border(1.dp, PlayerChrome.Accent, shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Shown when a search returns no episode in the season. */
@Composable
internal fun NoEpisodeMatches(query: String) {
    Text(
        "No episode matches “$query”",
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 13.sp,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

/** The reference app's card: one row of icon, label, and value cells. */
@Composable
internal fun PlayerValueCard(
    sourceLabel: String,
    qualityLabel: String,
    qualitySelected: Boolean,
    onQuality: () -> Unit,
    audioLabel: String,
    audioSelected: Boolean,
    onAudio: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF14151A))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ValueCell(
            icon = Icons.Rounded.Dns,
            label = "Source",
            value = sourceLabel,
            accent = false,
            onClick = null,
            modifier = Modifier.weight(1f),
        )
        ValueCellDivider()
        ValueCell(
            icon = Icons.Rounded.Hd,
            label = "Quality",
            value = qualityLabel,
            accent = qualitySelected,
            onClick = onQuality,
            modifier = Modifier.weight(1f),
        )
        ValueCellDivider()
        ValueCell(
            icon = Icons.Rounded.GraphicEq,
            label = "Audio",
            value = audioLabel,
            accent = audioSelected,
            onClick = onAudio,
            modifier = Modifier.weight(1f),
        )
        ValueCellDivider()
        ValueCell(
            icon = Icons.Rounded.MoreHoriz,
            label = "More",
            value = "Settings",
            accent = false,
            onClick = onMore,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ValueCell(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (accent) PlayerChrome.Accent else Color.White.copy(alpha = 0.88f),
            modifier = Modifier.size(22.dp),
        )
        Text(
            label,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 7.dp),
        )
        Text(
            value,
            color = if (accent) PlayerChrome.Accent else Color.White.copy(alpha = 0.55f),
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun ValueCellDivider() {
    Box(
        modifier = Modifier
            .size(width = 1.dp, height = 52.dp)
            .background(Color.White.copy(alpha = 0.08f)),
    )
}

/** Row of round actions under the card: share, save, watched, episodes. */
@Composable
internal fun PlayerActionRow(
    onShare: () -> Unit,
    shareEnabled: Boolean,
    onDownload: () -> Unit,
    downloadEnabled: Boolean,
    downloadSaved: Boolean,
    watched: Boolean,
    watchedEnabled: Boolean,
    onToggleWatched: () -> Unit,
    onEpisodes: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ActionCircle(
            icon = Icons.Rounded.Share,
            label = "Share",
            active = false,
            enabled = shareEnabled,
            onClick = onShare,
            modifier = Modifier.weight(1f),
        )
        ActionCircle(
            icon = Icons.Rounded.Download,
            label = if (downloadSaved) "Saved" else "Download",
            active = downloadSaved,
            enabled = downloadEnabled,
            onClick = onDownload,
            modifier = Modifier.weight(1f),
        )
        ActionCircle(
            icon = Icons.Rounded.Check,
            label = "Watched",
            active = watched,
            enabled = watchedEnabled,
            onClick = onToggleWatched,
            modifier = Modifier.weight(1f),
        )
        ActionCircle(
            icon = Icons.Rounded.GridView,
            label = "Episodes",
            active = false,
            enabled = true,
            onClick = onEpisodes,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActionCircle(
    icon: ImageVector,
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    if (active) {
                        PlayerChrome.Accent.copy(alpha = 0.14f)
                    } else {
                        Color.White.copy(alpha = 0.04f)
                    },
                )
                .border(
                    1.dp,
                    if (active) {
                        PlayerChrome.Accent.copy(alpha = 0.85f)
                    } else {
                        Color.White.copy(alpha = 0.14f)
                    },
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = when {
                    !enabled -> Color.White.copy(alpha = 0.3f)
                    active -> PlayerChrome.Accent
                    else -> Color.White.copy(alpha = 0.88f)
                },
                modifier = Modifier.size(23.dp),
            )
        }
        Text(
            label,
            color = when {
                !enabled -> Color.White.copy(alpha = 0.35f)
                active -> PlayerChrome.Accent
                else -> Color.White.copy(alpha = 0.75f)
            },
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 9.dp),
        )
    }
}
