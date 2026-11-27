// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.anikitty.app.ui.tv.awaitTvFocus
import site.anikitty.app.ui.theme.PlayerChrome

internal enum class PlayerMenuPage {
    None,
    Root,
    Speed,
    Quality,
    Subs,
    Size,
    Timing,
    Volume,
    Audio,
    Color,
    Edge,
    Background,
    Resize,
    Sleep,
    Episodes,
}

internal fun playerMenuTitle(page: PlayerMenuPage): String = when (page) {
    PlayerMenuPage.Speed -> "Play Speed"
    PlayerMenuPage.Quality -> "Quality"
    PlayerMenuPage.Subs -> "Subtitles"
    PlayerMenuPage.Size -> "Subtitle Size"
    PlayerMenuPage.Timing -> "Subtitle Position"
    PlayerMenuPage.Volume -> "Volume Boost"
    PlayerMenuPage.Audio -> "Audio Tracks"
    PlayerMenuPage.Color -> "Subtitle Color"
    PlayerMenuPage.Edge -> "Subtitle Edge"
    PlayerMenuPage.Background -> "Subtitle Background"
    PlayerMenuPage.Resize -> "Picture"
    PlayerMenuPage.Sleep -> "Sleep timer"
    PlayerMenuPage.Episodes -> "Episodes"
    PlayerMenuPage.None, PlayerMenuPage.Root -> "Settings"
}

/** Caption look pages stay open while you tweak — no accidental dimmer dismiss. */
internal fun PlayerMenuPage.isStickyStylePage(): Boolean = when (this) {
    PlayerMenuPage.Size,
    PlayerMenuPage.Timing,
    PlayerMenuPage.Color,
    PlayerMenuPage.Edge,
    PlayerMenuPage.Background,
    PlayerMenuPage.Subs,
    -> true
    else -> false
}

private val LocalMenuCompact = staticCompositionLocalOf { false }

/** One open menu page claims a single initial D-pad target. */
internal class MenuFocusGate {
    private var claimed = false

    fun claim(): Boolean {
        if (claimed) return false
        claimed = true
        return true
    }
}

private val LocalMenuFocusGate = staticCompositionLocalOf<MenuFocusGate?> { null }

@Composable
internal fun Modifier.menuInitialFocus(enabled: Boolean = true): Modifier {
    val gate = LocalMenuFocusGate.current
    val claim = remember(gate, enabled) { enabled && gate?.claim() == true }
    val requester = remember { FocusRequester() }
    if (claim) {
        LaunchedEffect(gate) { awaitTvFocus(requester) }
    }
    return if (claim) this.then(Modifier.focusRequester(requester)) else this
}

/**
 * Sized from the space left after cutouts and system bars.
 * A short landscape phone stays above the pills. A narrow portrait
 * phone uses a bottom sheet. A tablet stays a 360dp card.
 */
@Composable
internal fun PlayerMenuFrame(
    theater: Boolean,
    page: PlayerMenuPage,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    focusGate: MenuFocusGate? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.navigationBars
                    .union(WindowInsets.displayCutout)
                    .union(WindowInsets.statusBars),
            ),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            val shortSide = if (maxWidth < maxHeight) maxWidth else maxHeight
            val tablet = shortSide >= 600.dp
            val compactHeight = maxHeight < 480.dp
            // Landscape keeps the video on the left and the drawer on the right.
            // Portrait uses a bottom sheet.
            val side = landscape
            val sheet = !side
            val sticky = page.isStickyStylePage()
            val tall = page == PlayerMenuPage.Episodes
            Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.34f))
                        .then(
                            if (sticky) {
                                // Keep Size / Color / … open while previewing on the video.
                                Modifier.pointerInput(page) { detectTapGestures { } }
                            } else {
                                Modifier.clickable(onClick = onDismiss)
                            },
                        ),
                )
            val aboveControls = if (compactHeight) 104.dp else 116.dp
            val panelMax = when {
                tall -> maxHeight * 0.82f
                sticky -> maxHeight * 0.72f
                else -> maxHeight * 0.64f
            }
            val panelWidth = when {
                tablet && side -> 380.dp
                side -> 340.dp
                tablet -> 420.dp
                else -> maxWidth
            }
            val shape = if (side) {
                RoundedCornerShape(24.dp)
            } else {
                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            }
            Column(
                modifier = Modifier
                    .align(if (side) Alignment.CenterEnd else Alignment.BottomCenter)
                    .padding(
                        start = if (side) 0.dp else if (tablet) 12.dp else 0.dp,
                        end = if (side || tablet) 12.dp else 0.dp,
                        bottom = if (!side && theater && landscape) aboveControls else if (side) 12.dp else 0.dp,
                    )
                    .then(if (side || tablet) Modifier.width(panelWidth) else Modifier.fillMaxWidth())
                    .then(if (side) Modifier.heightIn(max = maxHeight * 0.94f) else Modifier.heightIn(max = panelMax))
                    .clip(shape)
                    .background(Color(0xD9101010))
                    .border(1.dp, Color(0xFF292929), shape)
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                if (sheet) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .align(Alignment.CenterHorizontally)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.18f)),
                    )
                }
                if (page != PlayerMenuPage.None) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (compactHeight) 48.dp else 52.dp)
                            .padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (page != PlayerMenuPage.Root) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .playerFocusCue(CircleShape)
                                    .clickable(role = Role.Button, onClick = onBack),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(if (compactHeight) 20.dp else 22.dp),
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        Text(
                            playerMenuTitle(page),
                            color = Color.White,
                            fontSize = if (compactHeight) 16.sp else 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .playerFocusCue(CircleShape)
                                .clickable(role = Role.Button, onClick = onDismiss)
                                .clearAndSetSemantics { contentDescription = "Close menu" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Done",
                                color = PlayerChrome.Accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(remember(page) { ScrollState(0) }),
                ) {
                    CompositionLocalProvider(
                        LocalMenuCompact provides compactHeight,
                        LocalMenuFocusGate provides focusGate,
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlayerMenuRow(
    label: String,
    value: String = "",
    icon: ImageVector? = null,
    glyph: String? = null,
    selected: Boolean = false,
    showChevron: Boolean = false,
    onClick: () -> Unit,
) {
    val compact = LocalMenuCompact.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .height(if (compact) 48.dp else 56.dp)
            .menuInitialFocus()
            .onFocusChanged { focused = it.hasFocus }
            .clip(shape)
            .background(
                when {
                    focused -> Color.White.copy(alpha = 0.14f)
                    selected -> PlayerChrome.Accent.copy(alpha = 0.14f)
                    else -> Color.White.copy(alpha = 0.04f)
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = menuRowDescription(label, value, selected)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(6.dp))
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            when {
                icon != null -> Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) PlayerChrome.Accent else Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(if (compact) 20.dp else 22.dp),
                )
                glyph != null -> Text(
                    glyph,
                    color = Color.White,
                    fontSize = if (compact) 16.sp else 18.sp,
                    fontFamily = FontFamily.SansSerif,
                )
            }
        }
        Text(
            label,
            color = Color.White,
            fontSize = if (compact) 14.sp else 15.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.weight(1f),
        )
        if (value.isNotEmpty()) {
            Text(
                value,
                color = if (selected) PlayerChrome.Accent else Color.White.copy(alpha = 0.58f),
                fontSize = if (compact) 13.sp else 14.sp,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        if (showChevron) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.42f),
                modifier = Modifier.size(20.dp),
            )
        } else if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = PlayerChrome.Accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Preview(name = "320x640", widthDp = 320, heightDp = 640)
@Composable
private fun Preview320x640() = MenuPreview(sheet = true)

@Preview(name = "360x800", widthDp = 360, heightDp = 800)
@Composable
private fun Preview360x800() = MenuPreview(sheet = true)

@Preview(name = "800x360", widthDp = 800, heightDp = 360)
@Composable
private fun Preview800x360() = MenuPreview(sheet = false)

@Preview(name = "640x320", widthDp = 640, heightDp = 320)
@Composable
private fun Preview640x320() = MenuPreview(sheet = false)

@Preview(name = "Tablet landscape", widthDp = 1280, heightDp = 800)
@Composable
private fun PreviewTabletLandscape() = MenuPreview(sheet = false)

@Preview(name = "Tablet portrait", widthDp = 800, heightDp = 1280)
@Composable
private fun PreviewTabletPortrait() = MenuPreview(sheet = false)

@Composable
private fun MenuPreview(sheet: Boolean) {
    Box(Modifier.fillMaxSize().background(Color(0xFF1A1A1A))) {
        PlayerMenuFrame(
            theater = !sheet,
            page = PlayerMenuPage.Root,
            onBack = {},
            onDismiss = {},
        ) {
            PreviewRows()
        }
        if (!sheet) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 22.dp, bottom = 20.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.58f))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("pills", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
    }
}

@Composable
internal fun PlayerDrawerTabs(
    page: PlayerMenuPage,
    onSelect: (PlayerMenuPage) -> Unit,
) {
    val tabs = listOf(
        PlayerMenuPage.Episodes to "Episodes",
        PlayerMenuPage.Subs to "Audio & Subs",
        PlayerMenuPage.Quality to "Quality & Source",
        PlayerMenuPage.Speed to "Speed",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEach { (id, label) ->
            val selected = page == id || (id == PlayerMenuPage.Subs && page == PlayerMenuPage.Audio)
            val shape = RoundedCornerShape(999.dp)
            Text(
                label,
                color = if (selected) Color(0xFF080808) else Color(0xFFA1A1A1),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .clip(shape)
                    .background(if (selected) Color.White else Color(0xFF151515))
                    .border(1.dp, if (selected) Color.White else Color(0xFF292929), shape)
                    .clickable { onSelect(id) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PreviewRows() {
    PlayerMenuRow(label = "Play Speed", value = "Normal", icon = Icons.Outlined.PlayCircle, showChevron = true, onClick = {})
    PlayerMenuRow(label = "Quality", value = "Auto (1080p)", icon = Icons.Outlined.Equalizer, showChevron = true, onClick = {})
    PlayerMenuRow(label = "Subtitles", value = "English", icon = Icons.Outlined.ClosedCaption, showChevron = true, onClick = {})
    PlayerMenuRow(label = "Subtitle Size", value = "100%", glyph = "T", showChevron = true, onClick = {})
    PlayerMenuRow(label = "Subtitle Timing", value = "High", icon = Icons.Outlined.Schedule, showChevron = true, onClick = {})
    PlayerMenuRow(label = "Volume Boost", value = "Off", showChevron = true, onClick = {})
    PlayerMenuRow(label = "Audio Tracks", value = "Sub", showChevron = true, onClick = {})
    PlayerMenuRow(label = "Episodes", icon = Icons.Outlined.VideoLibrary, showChevron = true, onClick = {})
    PlayerMenuRow(label = "Next episode", icon = Icons.Outlined.SkipNext, showChevron = true, onClick = {})
}
