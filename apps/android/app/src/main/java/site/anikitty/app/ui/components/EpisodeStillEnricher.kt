// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import site.anikitty.app.data.anilist.AnimeSummary
import site.anikitty.app.data.servers.EpisodeArtCatalog
import site.anikitty.app.data.servers.SiteEpisodeArt
import site.anikitty.app.data.tmdb.TitleArt
import site.anikitty.app.data.tmdb.TmdbCatalog
import site.anikitty.app.data.tmdb.preferEpisodeTitle

/**
 * Series cover used as a filler thumb is not a real episode still — treat it
 * as missing so site / TMDB / AniList streaming art can win.
 */
fun realEpisodeThumb(url: String?, seriesCoverUrl: String?): String? {
    val clean = url?.trim().orEmpty()
    if (clean.isEmpty() || !clean.startsWith("http")) return null
    val cover = seriesCoverUrl?.trim().orEmpty()
    if (cover.isNotEmpty() && clean == cover) return null
    return clean
}

/** First HTTP image wins — site scrapes beat AniList fillers and TMDB. */
fun preferEpisodeImage(vararg candidates: String?): String? =
    candidates.firstOrNull { it?.startsWith("http") == true }

/**
 * Merge site episode stills (AniGato / AniZip), AniList streaming thumbs,
 * and TMDB season stills for every episode surface.
 */
fun enrichEpisodeSlides(
    slides: List<EpisodeSlide>,
    art: TitleArt?,
    siteArt: Map<Int, SiteEpisodeArt> = emptyMap(),
    seriesCoverUrl: String? = null,
): List<EpisodeSlide> {
    if (slides.isEmpty()) return slides
    val episodes = art?.episodes.orEmpty()
    if (episodes.isEmpty() && siteArt.isEmpty() && seriesCoverUrl == null) return slides
    return slides.map { slide ->
        val episodeArt = episodes[slide.number]
        val site = siteArt[slide.number]
        val base = realEpisodeThumb(slide.imageUrl, seriesCoverUrl)
        slide.copy(
            title = preferEpisodeTitle(
                preferEpisodeTitle(slide.title, slide.number, site?.title),
                slide.number,
                episodeArt?.title,
            ),
            imageUrl = preferEpisodeImage(
                site?.imageUrl,
                base,
                episodeArt?.stillUrl,
            ),
            airLabel = episodeArt?.airLabel ?: slide.airLabel,
            overview = site?.overview ?: episodeArt?.overview ?: slide.overview,
            runtimeMinutes = episodeArt?.runtimeMinutes ?: slide.runtimeMinutes,
        )
    }
}

/**
 * Loads live-site + AniZip episode stills (and TMDB as a backfill) so the
 * player selector and title page show real episode frames.
 */
@Composable
fun rememberEnrichedEpisodeSlides(
    anilistId: Int,
    title: String,
    isMovie: Boolean,
    totalEpisodes: Int,
    slides: List<EpisodeSlide>,
    seriesCoverUrl: String?,
    titleHint: String? = null,
    malId: Int? = null,
    loadSiteArt: (suspend (Int) -> Map<Int, SiteEpisodeArt>)? = null,
): List<EpisodeSlide> {
    val context = LocalContext.current
    var art by remember(anilistId) { mutableStateOf<TitleArt?>(null) }
    var siteArt by remember(anilistId) { mutableStateOf<Map<Int, SiteEpisodeArt>>(emptyMap()) }
    LaunchedEffect(anilistId, title, isMovie, totalEpisodes, titleHint, malId, loadSiteArt) {
        if (anilistId <= 0) {
            art = null
            siteArt = emptyMap()
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) {
            loadEpisodeArtBundle(
                context = context,
                anilistId = anilistId,
                title = title,
                isMovie = isMovie,
                totalEpisodes = totalEpisodes,
                titleHint = titleHint,
                malId = malId,
                loadSiteArt = loadSiteArt,
            )
        }
        art = loaded.tmdb
        siteArt = loaded.site
    }
    return remember(slides, art, siteArt, seriesCoverUrl) {
        enrichEpisodeSlides(slides, art, siteArt, seriesCoverUrl)
    }
}

internal data class EpisodeArtBundle(
    val tmdb: TitleArt?,
    val site: Map<Int, SiteEpisodeArt>,
)

internal suspend fun loadEpisodeArtBundle(
    context: Context,
    anilistId: Int,
    title: String,
    isMovie: Boolean,
    totalEpisodes: Int,
    titleHint: String? = null,
    malId: Int? = null,
    loadSiteArt: (suspend (Int) -> Map<Int, SiteEpisodeArt>)? = null,
): EpisodeArtBundle = coroutineScope {
    val siteDeferred = async(Dispatchers.IO) {
        loadSiteArt?.invoke(anilistId)
            ?: EpisodeArtCatalog.shared(context).load(anilistId)
    }
    val tmdbDeferred = async(Dispatchers.IO) {
        loadTitleArtForSlides(
            context = context,
            anilistId = anilistId,
            title = title,
            isMovie = isMovie,
            totalEpisodes = totalEpisodes,
            titleHint = titleHint,
            malId = malId,
        )
    }
    EpisodeArtBundle(
        tmdb = tmdbDeferred.await(),
        site = siteDeferred.await(),
    )
}

internal fun loadTitleArtForSlides(
    context: Context,
    anilistId: Int,
    title: String,
    isMovie: Boolean,
    totalEpisodes: Int,
    titleHint: String? = null,
    malId: Int? = null,
): TitleArt? {
    if (anilistId <= 0) return null
    val parsedMal = malId
        ?: Regex("""mal:(\d+)""")
            .find(titleHint.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    val seed = AnimeSummary(
        id = anilistId,
        title = title.ifBlank { "Anime" },
        coverImageUrl = null,
        bannerImageUrl = null,
        episodes = totalEpisodes.takeIf { it > 0 },
        format = if (isMovie) "MOVIE" else "TV",
        averageScore = null,
        malId = parsedMal,
    )
    return TmdbCatalog.shared(context).load(seed)
}
