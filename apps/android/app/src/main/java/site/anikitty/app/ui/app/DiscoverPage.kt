// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import site.anikitty.app.data.anilist.AniListClient
import site.anikitty.app.data.anilist.AnimeSummary
import site.anikitty.app.data.friendlyFailure
import site.anikitty.app.ui.components.KittyBottomSheet
import site.anikitty.app.ui.components.KittyChip
import site.anikitty.app.ui.components.StageGlassDisc
import site.anikitty.app.ui.components.stageGlassPlate
import site.anikitty.app.ui.icons.FluentuiSystemIconsCalendarClockFilled
import site.anikitty.app.ui.icons.FluentuiSystemIconsStarFilled
import site.anikitty.app.ui.theme.KittyRadius
import site.anikitty.app.ui.theme.LocalKittyPalette
import site.anikitty.app.ui.theme.LookState
import site.anikitty.app.ui.theme.ShimmerBox
import site.anikitty.app.ui.theme.kittyPageBrush

/** Genres worth a one-tap entry on Discover. */
private val DISCOVER_GENRES = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Fantasy",
    "Horror", "Mystery", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Thriller",
)

private class FilterJob {
    var job: Job? = null
}

private val DISCOVER_CATEGORIES = listOf(
    "Trending",
    "Popular",
    "Top Rated",
    "Seasonal",
    "Upcoming",
    "Recently Added",
    "Hidden Gems",
)

private data class DiscoverFilter(
    val genre: String? = null,
    val year: Int? = null,
    val season: String? = null,
    val format: String? = null,
    val status: String? = null,
    val minScore: Int? = null,
    val sort: String = "POPULARITY_DESC",
) {
    fun active(): Boolean =
        genre != null || year != null || season != null || format != null ||
            status != null || minScore != null || sort != "POPULARITY_DESC"
}

/**
 * Catalog destination. Category chips switch a real AniList grid.
 * Filters reuse the existing browse query — nothing is invented.
 */
@Composable
fun DiscoverFeed(
    aniList: AniListClient,
    demo: Boolean = false,
    onOpen: (AnimeSummary) -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenGenre: (String) -> Unit,
    onOpenRandom: (AnimeSummary) -> Unit,
    onOpenSearch: () -> Unit = {},
    bottomInset: Dp = 96.dp,
) {
    val palette = LocalKittyPalette.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var trending by remember { mutableStateOf<List<AnimeSummary>>(emptyList()) }
    var popular by remember { mutableStateOf<List<AnimeSummary>>(emptyList()) }
    var topRated by remember { mutableStateOf<List<AnimeSummary>>(emptyList()) }
    var gems by remember { mutableStateOf<List<AnimeSummary>>(emptyList()) }
    var upcoming by remember { mutableStateOf<List<AnimeSummary>>(emptyList()) }
    var seasonNow by remember { mutableStateOf<List<AnimeSummary>>(emptyList()) }
    var recent by remember { mutableStateOf<List<AnimeSummary>>(emptyList()) }
    var category by rememberSaveable { mutableStateOf("Trending") }
    var sheet by remember { mutableStateOf(false) }
    var applied by remember { mutableStateOf(DiscoverFilter()) }
    var filtered by remember { mutableStateOf<List<AnimeSummary>>(emptyList()) }
    var filtering by remember { mutableStateOf(false) }
    val filterJob = remember { FilterJob() }

    fun reload() {
        if (demo) {
            trending = site.anikitty.app.data.demo.DemoLibrary.featured
            popular = site.anikitty.app.data.demo.DemoLibrary.seasonal
            topRated = site.anikitty.app.data.demo.DemoLibrary.featured.take(6)
            recent = site.anikitty.app.data.demo.DemoLibrary.catalog
            loading = false
            return
        }
        // Paint last-known shelves immediately so Discover never blank-shimmers.
        if (HomeShelfMemory.trending.isNotEmpty()) {
            trending = HomeShelfMemory.trending
            popular = HomeShelfMemory.popular.ifEmpty { HomeShelfMemory.seasonal }
            seasonNow = HomeShelfMemory.seasonal
            loading = false
        } else {
            loading = true
        }
        error = null
        scope.launch {
            runCatching {
                coroutineScope {
                    val now = java.util.Calendar.getInstance()
                    val season = when (now.get(java.util.Calendar.MONTH)) {
                        in 0..2 -> "WINTER"
                        in 3..5 -> "SPRING"
                        in 6..8 -> "SUMMER"
                        else -> "FALL"
                    }
                    val year = now.get(java.util.Calendar.YEAR)
                    val t = async { aniList.trending() }
                    val p = async { aniList.popular() }
                    val r = async {
                        aniList.browse(sort = "SCORE_DESC", perPage = 24)
                            .filter { (it.averageScore ?: 0) >= 78 }
                            .take(18)
                    }
                    val g = async { hiddenGemTitles(aniList) }
                    val u = async {
                        aniList.browse(status = "NOT_YET_RELEASED", sort = "POPULARITY_DESC", perPage = 18)
                    }
                    val s = async {
                        aniList.browse(season = season, seasonYear = year, sort = "TRENDING_DESC", perPage = 18)
                    }
                    val n = async { aniList.browse(sort = "ID_DESC", perPage = 18) }
                    trending = t.await()
                    popular = p.await()
                    topRated = r.await()
                    gems = g.await()
                    upcoming = u.await()
                    seasonNow = s.await()
                    recent = n.await()
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                error = friendlyFailure(e.message)
            }
            loading = false
        }
    }

    fun apply(next: DiscoverFilter) {
        applied = next
        sheet = false
        filterJob.job?.cancel()
        if (!next.active()) {
            filtered = emptyList()
            filtering = false
            return
        }
        if (demo) {
            val base = site.anikitty.app.data.demo.DemoLibrary.catalog
            filtered = base.filter { anime ->
                (next.genre == null || next.genre in anime.genres) &&
                    (next.minScore == null || (anime.averageScore ?: 0) >= next.minScore)
            }
            return
        }
        filtering = true
        filterJob.job = scope.launch {
            val found = runCatching {
                aniList.browse(
                    genre = next.genre,
                    season = next.season,
                    seasonYear = next.year,
                    format = next.format,
                    status = next.status,
                    minScore = next.minScore,
                    sort = next.sort,
                    perPage = 24,
                )
            }.getOrElse { e ->
                if (e is CancellationException) throw e
                error = friendlyFailure(e.message)
                emptyList()
            }
            filtered = found.distinctBy { it.id }
            filtering = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    val pool = remember(trending, popular) {
        (trending + popular).distinctBy { it.id }
    }
    val categoryItems = when (category) {
        "Popular" -> popular
        "Top Rated" -> topRated
        "Seasonal" -> seasonNow
        "Upcoming" -> upcoming
        "Recently Added" -> recent
        "Hidden Gems" -> gems
        else -> trending
    }
    val grid = (if (applied.active()) filtered else categoryItems).distinctBy { it.id }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val columns = posterColumns((maxWidth - 112.dp).coerceAtLeast(160.dp))
    val openTitle by rememberUpdatedState(onOpen)
    val posterRows = remember(grid, columns) { grid.chunked(columns) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kittyPageBrush(LookState.look != "amoled"))
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "CATALOG",
                    color = palette.muted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.8.sp,
                )
                Text(
                    "Discover",
                    color = palette.text,
                    fontWeight = FontWeight.Black,
                    fontSize = 32.sp,
                    letterSpacing = (-0.8).sp,
                )
            }
            DiscoverIconButton(Icons.Outlined.Search, "Search", onOpenSearch)
            DiscoverIconButton(Icons.Outlined.Tune, "Filters", { sheet = true })
        }
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .width(112.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 8.dp, bottom = bottomInset),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                DISCOVER_CATEGORIES.forEach { name ->
                    val on = !applied.active() && category == name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .spacePress {
                                category = name
                                if (applied.active()) apply(DiscoverFilter())
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .width(2.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(if (on) Color.White else Color.Transparent),
                        )
                        Text(
                            name,
                            color = if (on) palette.text else palette.muted,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(bottom = bottomInset),
            ) {
                if (loading && trending.isEmpty()) {
                    item { ShimmerBox(modifier = Modifier.fillMaxWidth().padding(16.dp).height(96.dp)) }
                    item { ShimmerBox(modifier = Modifier.fillMaxWidth().padding(16.dp).height(220.dp)) }
                    return@LazyColumn
                }
                if (error != null && trending.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(error ?: "Couldn't load the catalog", color = palette.text, fontSize = 14.sp)
                            OutlinedButton(onClick = { reload() }) {
                                Text("Try again", color = palette.text)
                            }
                        }
                    }
                    return@LazyColumn
                }
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DiscoverDoor(
                            icon = FluentuiSystemIconsCalendarClockFilled,
                            title = "Airing schedule",
                            subtitle = "Next 5 days",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onOpenSchedule,
                        )
                        DiscoverDoor(
                            icon = FluentuiSystemIconsStarFilled,
                            title = "Surprise me",
                            subtitle = "Random pick",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { if (pool.isNotEmpty()) onOpenRandom(pool.random()) },
                        )
                    }
                }
                item {
                    Text(
                        if (applied.active()) "Filtered" else category,
                        color = palette.text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
                    )
                }
                if (filtering) {
                    item { ShimmerBox(modifier = Modifier.fillMaxWidth().padding(16.dp).height(180.dp)) }
                } else if (grid.isEmpty()) {
                    item {
                        Text(
                            if (applied.active()) "Nothing matches these filters." else "Nothing in $category right now.",
                            color = palette.muted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                } else {
                    posterRows.forEach { row ->
                        item(key = "grid-${row.first().id}", contentType = "poster-row") {
                            HomePosterLine(row = row, columns = columns, onOpen = { openTitle(it) })
                        }
                    }
                }
            }
        }
    }
    }

    if (sheet) {
        DiscoverFilterSheet(
            initial = applied,
            onDismiss = { sheet = false },
            onApply = { apply(it) },
            onReset = { apply(DiscoverFilter()) },
        )
    }
}

/**
 * Hidden gems heuristic: well-scored AniList titles with a modest audience.
 * Requires a minimum sample so one-vote outliers never qualify.
 */
internal suspend fun hiddenGemTitles(aniList: AniListClient): List<AnimeSummary> {
    val candidates = aniList.browse(sort = "SCORE_DESC", perPage = 40)
    return candidates
        .filter { anime ->
            val score = anime.averageScore ?: return@filter false
            val popularity = anime.popularity ?: return@filter false
            score in 78..100 && popularity < 120_000
        }
        .take(12)
}

@Composable
private fun DiscoverFilterSheet(
    initial: DiscoverFilter,
    onDismiss: () -> Unit,
    onApply: (DiscoverFilter) -> Unit,
    onReset: () -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    val yearNow = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val palette = LocalKittyPalette.current
    KittyBottomSheet(title = "Filters", onDismiss = onDismiss) {
        Text("Genre", color = palette.muted, fontSize = 12.sp)
        FilterChipRow(DISCOVER_GENRES, draft.genre) { draft = draft.copy(genre = it) }
        Text("Release year", color = palette.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        FilterChipRow((yearNow downTo yearNow - 8).map { it.toString() }, draft.year?.toString()) {
            draft = draft.copy(year = it?.toIntOrNull())
        }
        Text("Season", color = palette.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        FilterChipRow(listOf("WINTER", "SPRING", "SUMMER", "FALL"), draft.season) {
            draft = draft.copy(season = it)
        }
        Text("Format", color = palette.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        FilterChipRow(listOf("TV", "MOVIE", "OVA", "ONA"), draft.format) { draft = draft.copy(format = it) }
        Text("Status", color = palette.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        FilterChipRow(listOf("RELEASING", "FINISHED", "NOT_YET_RELEASED"), draft.status) {
            draft = draft.copy(status = it)
        }
        Text("Minimum rating", color = palette.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        FilterChipRow(listOf("70", "75", "80", "85"), draft.minScore?.toString()) {
            draft = draft.copy(minScore = it?.toIntOrNull())
        }
        Text(
            "Sub and dub are chosen per episode in the player. The catalog does not filter by audio.",
            color = palette.faint,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text("Sorting", color = palette.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        FilterChipRow(
            listOf("POPULARITY_DESC", "TRENDING_DESC", "SCORE_DESC", "START_DATE_DESC", "ID_DESC"),
            draft.sort,
            allowClear = false,
        ) { draft = draft.copy(sort = it ?: "POPULARITY_DESC") }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                Text("Reset", color = palette.text)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("Dismiss", color = palette.muted)
            }
            androidx.compose.material3.Button(
                onClick = { onApply(draft) },
                modifier = Modifier.weight(1.2f),
                shape = RoundedCornerShape(KittyRadius.button),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = palette.text,
                    contentColor = palette.page,
                ),
            ) {
                Text("Apply", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun FilterChipRow(
    values: List<String>,
    selected: String?,
    allowClear: Boolean = true,
    onPick: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (allowClear) {
            KittyChip(label = "Any", selected = selected == null, onClick = { onPick(null) })
        }
        values.forEach { value ->
            val label = value.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
            KittyChip(label = label, selected = selected == value, onClick = { onPick(value) })
        }
    }
}

@Composable
private fun DiscoverIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val palette = LocalKittyPalette.current
    StageGlassDisc(onClick = onClick, size = 48.dp) {
        Icon(icon, contentDescription = label, tint = palette.text, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun DiscoverDoor(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalKittyPalette.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .stageGlassPlate(shape = shape)
            .spacePress(onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = palette.text, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                title,
                color = palette.text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, color = palette.muted, fontSize = 11.sp, maxLines = 1)
        }
    }
}
