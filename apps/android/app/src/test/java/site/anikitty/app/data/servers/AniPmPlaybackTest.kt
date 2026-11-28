// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.anikitty.app.data.extensions.isRetiredExtension

class AniPmPlaybackTest {
    @Test
    fun onlyOneBundledAniPmCard() {
        assertEquals("anipm", AniPmServer.ID)
        assertEquals("ani.pm", AniPmServer(cache = null).server.name)
        assertEquals(AniPmServer.SITE, AniPmServer(cache = null).server.siteUrl)
        assertEquals(
            listOf(
                "https://anipm.pk",
                "https://ani.pm",
                "https://ani.nz",
                "https://ani.vg",
            ),
            AniPmServer.BRAND_ORIGINS,
        )
        assertEquals(listOf(AniPmServer.PAPA), AniPmServer.FALLBACK_ORIGINS)
        assertEquals(
            AniPmServer.BRAND_ORIGINS + AniPmServer.FALLBACK_ORIGINS,
            AniPmServer.ORIGINS,
        )
        // Brand first — Papa is a different product, only a CF fallback.
        assertFalse(AniPmServer.BRAND_ORIGINS.contains(AniPmServer.PAPA))
        assertEquals(AniPmServer.PAPA, AniPmServer.ORIGINS.last())
        assertEquals(AniPmServer.PAPA, HinataTvServer.PAPA)
        assertFalse(isRetiredExtension("anipm", "ani.pm", "https://ani.pm"))
        // Second / community ani.pm packs + former ani.nz card are retired.
        assertTrue(isRetiredExtension("site.anikitty.anipm", "Ani.pm", "https://anipm.pk"))
        assertTrue(isRetiredExtension("anipm-mirror", "ani.pm", null))
        assertTrue(isRetiredExtension("community-pack", "Something", "https://anipm.pk/watch"))
        assertTrue(isRetiredExtension("aninz", "ani.nz", "https://ani.nz"))
        assertTrue(isRetiredExtension("mirror", "Something", "https://ani.nz/watch"))
        assertTrue(isRetiredExtension("pack", "ani.vg", "https://ani.vg"))
        assertFalse(AniPmServer.MISSING.contains("404"))
        assertFalse(AniPmServer.BLOCKED.contains("403"))
    }

    @Test
    fun orderedOriginsPreferWarmMirror() {
        assertEquals(
            listOf(
                "https://ani.nz",
                "https://anipm.pk",
                "https://ani.pm",
                "https://ani.vg",
            ),
            anipmOrderedOrigins(
                AniPmServer.BRAND_ORIGINS,
                "https://ani.nz/embed/mal/13601",
            ),
        )
        assertEquals(
            AniPmServer.BRAND_ORIGINS,
            anipmOrderedOrigins(AniPmServer.BRAND_ORIGINS, null),
        )
        // Papa watch URLs must not pin a brand race — not in BRAND_ORIGINS.
        assertNull(
            anipmOriginFromWatchUrl(
                "${AniPmServer.PAPA}/embed/mal/21",
                AniPmServer.BRAND_ORIGINS,
            ),
        )
        assertEquals(
            AniPmServer.PAPA,
            anipmOriginFromWatchUrl(
                "${AniPmServer.PAPA}/embed/mal/21",
                AniPmServer.FALLBACK_ORIGINS,
            ),
        )
        assertEquals(
            "https://anipm.pk",
            anipmOriginFromWatchUrl("https://anipm.pk/embed/mal/21", AniPmServer.BRAND_ORIGINS),
        )
        assertNull(anipmOriginFromWatchUrl("https://other.example/x", AniPmServer.ORIGINS))
    }

    @Test
    fun originHealthSkipsChallengedBrandEdges() {
        AnipmOriginHealth.clear()
        AnipmOriginHealth.markChallenged("https://ani.pm", nowMs = 1_000L)
        assertFalse(AnipmOriginHealth.shouldTry("https://ani.pm", nowMs = 1_000L + 1L))
        // Still inside the hold window — brand race must skip ani.pm.
        assertEquals(
            listOf("https://anipm.pk", "https://ani.nz", "https://ani.vg"),
            anipmUsableOrigins(AniPmServer.BRAND_ORIGINS, nowMs = 1_500L),
        )
        assertTrue(
            AnipmOriginHealth.shouldTry(
                "https://ani.pm",
                nowMs = 1_000L + AnipmOriginHealth.HOLD_MS + 1L,
            ),
        )
        AnipmOriginHealth.markChallenged("https://ani.pm", nowMs = 2_000L)
        AnipmOriginHealth.markOk("https://ani.pm")
        assertTrue(AnipmOriginHealth.shouldTry("https://ani.pm", nowMs = 2_500L))
        AnipmOriginHealth.clear()
    }

    @Test
    fun pickBestHitPrefersBrandQualityThenSpeed() {
        fun hit(
            origin: String,
            brand: Boolean,
            quality: String?,
            latencyMs: Long,
            subs: Int = 0,
        ): AnipmOriginHit {
            val tracks = List(subs) {
                SubtitleTrack(url = "https://x/$it.vtt", label = "en")
            }
            return AnipmOriginHit(
                origin = origin,
                source = PlayableSource(
                    url = "$origin/play.m3u8",
                    headers = emptyMap(),
                    label = quality ?: "SUB",
                    quality = quality,
                    subtitles = tracks,
                ),
                brand = brand,
                height = anipmSourceHeight(
                    PlayableSource(
                        url = "$origin/play.m3u8",
                        headers = emptyMap(),
                        quality = quality,
                    ),
                ),
                latencyMs = latencyMs,
            )
        }
        assertEquals(1080, anipmSourceHeight(PlayableSource(url = "u", headers = emptyMap(), quality = "1080p")))
        assertEquals(720, anipmSourceHeight(PlayableSource(url = "u", headers = emptyMap(), quality = "1280x720")))
        assertEquals(0, anipmSourceHeight(PlayableSource(url = "u", headers = emptyMap(), quality = null)))

        val papaFast = hit(AniPmServer.PAPA, brand = false, quality = "1080p", latencyMs = 80L)
        val brand720 = hit("https://anipm.pk", brand = true, quality = "720p", latencyMs = 200L)
        val brand1080Slow = hit("https://ani.nz", brand = true, quality = "1080p", latencyMs = 400L)
        val brand1080Fast = hit("https://ani.vg", brand = true, quality = "1080p", latencyMs = 120L, subs = 2)

        // Brand beats Papa even when Papa is faster / same height.
        assertEquals(brand720.origin, anipmPickBestHit(listOf(papaFast, brand720))?.origin)
        // Among brand: higher height wins.
        assertEquals(
            brand1080Slow.origin,
            anipmPickBestHit(listOf(brand720, brand1080Slow))?.origin,
        )
        // Same height: more subs, then lower RTT.
        assertEquals(
            brand1080Fast.origin,
            anipmPickBestHit(listOf(brand1080Slow, brand1080Fast, papaFast))?.origin,
        )
        assertNull(anipmPickBestHit(emptyList()))
    }

    @Test
    fun playerApiLookupAndSessionBuildPlaylist() {
        val lookup = """
            {"data":[{"found":true,"query":{"anilist":154587},
              "title":{"anilistId":154587,"malId":52991,"title":"Frieren: Beyond Journey's End"}}]}
        """.trimIndent()
        assertEquals(52991, anipmMalFromLookup(lookup, 154587))

        val titles = """
            {"data":[
              {"anilistId":21,"malId":21,"title":"One Piece"},
              {"anilistId":154587,"malId":52991,"title":"Frieren: Beyond Journey's End"}
            ],"total":2}
        """.trimIndent()
        assertEquals(52991, anipmPickTitle(titles, 154587, "Frieren"))

        val session = anipmParseSession(
            """{"expiresAt":1795830775,"token":"2.52991.1.tok"}""",
        )
        assertEquals("2.52991.1.tok", session?.token)
        // Wire expiry is Unix seconds — cache stores millis.
        assertEquals(1_795_830_775_000L, session?.expiresAt)
        assertEquals(1_795_830_775_000L, anipmExpiresAtMs(1_795_830_775L))
        assertEquals(1_795_830_775_000L, anipmExpiresAtMs(1_795_830_775_000L))
        assertEquals(0L, anipmExpiresAtMs(0L))
        assertEquals(
            "https://anipm.pk/playlists/52991_ep1/sub.direct.pls?t=2.52991.1.tok",
            anipmMediaPlaylistUrl("https://anipm.pk", 52991, 1, "sub", "2.52991.1.tok"),
        )
        assertEquals(
            "https://ani.pm/playlists/52991_ep1/dub.direct.pls?t=2.52991.1.tok",
            anipmMediaPlaylistUrl("https://ani.pm", 52991, 1, "dub", "2.52991.1.tok"),
        )
        assertTrue(anipmIsHls("#EXTM3U\n#EXTINF:4.0,\nseg.ts"))
        assertFalse(anipmIsHls("player required"))
        assertNull(anipmParseSession("""{"error":{"code":"media-unavailable"}}"""))
    }

    @Test
    fun skipAndSubtitlesParse() {
        val skip = anipmSkip("""{"op":{"start":10.5,"end":90.0},"ed":{"start":1300.0,"end":1350.0}}""")
        assertEquals(10.5, skip.intro?.startSec ?: -1.0, 0.01)
        assertEquals(1350.0, skip.outro?.endSec ?: -1.0, 0.01)

        val tracks = anipmSubtitles(
            """{"tracks":[{"default":true,"label":"English","lang":"en","type":"vtt","url":"/subtitles/52991/1/sub/1.vtt"}]}""",
            origin = "https://anipm.pk",
            token = "tok",
        )
        assertEquals(1, tracks.size)
        assertEquals("English", tracks[0].label)
        assertTrue(tracks[0].url.startsWith("https://anipm.pk/subtitles/"))
        assertTrue(tracks[0].url.contains("t=tok"))
        assertEquals(listOf("Frieren"), anipmTitles("Frieren || mal:52991"))
    }

    @Test
    fun sessionCacheSkipsColdRoundTripUntilSkew() {
        AnipmSessionCache.clear()
        // Live wire seconds → millis via parse; cache keys compare wall-clock millis.
        val parsed = anipmParseSession("""{"expiresAt":1795830775,"token":"warm-tok"}""")!!
        AnipmSessionCache.put("https://anipm.pk", 52991, 1, "sub", parsed)
        assertEquals(
            "warm-tok",
            AnipmSessionCache.get(
                "https://anipm.pk/",
                52991,
                1,
                "SUB",
                nowMs = parsed.expiresAt - AnipmSessionCache.SKEW_MS - 1_000L,
            )?.token,
        )
        // Inside skew window → miss so Media3 never rides an expiring token.
        assertNull(
            AnipmSessionCache.get(
                "https://anipm.pk",
                52991,
                1,
                "sub",
                nowMs = parsed.expiresAt - AnipmSessionCache.SKEW_MS + 1L,
            ),
        )
        // Papa origin is a separate lane from ani.pm brand.
        assertNull(AnipmSessionCache.get(HinataTvServer.PAPA, 52991, 1, "sub", nowMs = 1_000L))
        AnipmSessionCache.put(HinataTvServer.PAPA, 61990, 1, "dub", AnipmSession("papa", 9_999_999L))
        assertEquals(
            "papa",
            AnipmSessionCache.get(HinataTvServer.PAPA, 61990, 1, "dub", nowMs = 1_000L)?.token,
        )
        AnipmSessionCache.clear()
    }
}
