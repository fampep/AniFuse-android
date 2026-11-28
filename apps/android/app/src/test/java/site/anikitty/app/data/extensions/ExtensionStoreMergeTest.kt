// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionStoreMergeTest {
    @Test
    fun retiredNamesAreSettlarYozoraAndRemovedStores() {
        assertFalse(isRetiredExtension("anipm"))
        assertFalse(isRetiredExtension("anipm", "ani.pm"))
        assertTrue(isRetiredExtension("settlar", "Settlar"))
        assertTrue(isRetiredExtension("custom", "Yozora"))
        assertTrue(isRetiredExtension("animenexus", "Anime Nexus"))
        assertTrue(isRetiredExtension("miruro", "Miruro"))
        assertFalse(isRetiredExtension("reanime", "reanime"))
        assertFalse(isRetiredExtension("anigato", "AniGato"))
        // Only the bundled anipm card stays — every other ani.pm / ani.nz pack is retired.
        assertTrue(isRetiredExtension("site.anikitty.anipm", "Ani.pm"))
        assertTrue(isRetiredExtension("extra", "ani.pm", "https://anipm.pk"))
        assertTrue(isRetiredExtension("aninz", "ani.nz"))
        assertTrue(isRetiredExtension("legacy", "Ani NZ", "https://ani.nz"))
    }

    @Test
    fun newerMirrorKeepsLiveAniPmAndNewerReanime() {
        val merged = mergeStoreIndexes(
            listOf(
                index(
                    url = MIRROR,
                    updatedAt = "2026-11-15",
                    anipm(),
                    reanime(),
                ),
                index(
                    url = MONOREPO,
                    updatedAt = "2026-11-14",
                    anipm(),
                    reanime(version = "0.9.0"),
                ),
            ),
        )
        assertEquals(
            listOf("anipm", "reanime", "anirex", "animeheaven", "oneko", "twodhive", "hinatatv"),
            merged.extensions.map { it.id },
        )
        assertEquals("1.0.0", merged.extensions.first { it.id == "reanime" }.version)
        assertEquals("1.1", merged.extensions.first { it.id == "reanime" }.protocol)
        assertEquals("2026-11-15", merged.updatedAt)
    }

    @Test
    fun sameDayMirrorKeepsAniPmAlongsideCommunityRows() {
        val merged = mergeStoreIndexes(
            listOf(
                index(MIRROR, "2026-11-15", reanime()),
                index(MONOREPO, "2026-11-15", anipm(), hello()),
            ),
        )
        assertEquals(
            listOf(
                "reanime",
                "anipm",
                "site.anikitty.example.hello",
                "anirex",
                "animeheaven",
                "oneko",
                "twodhive",
                "hinatatv",
            ),
            merged.extensions.map { it.id },
        )
        assertEquals("ani.pm", merged.extensions.first { it.id == "anipm" }.name)
    }

    @Test
    fun publishedIndexListsAnifuseFirstAndDeprecatesProviders() {
        val listing = StoreListing(
            id = "site.anikitty.anifuse",
            name = "AniFuse",
            description = "main extension",
            github = "https://github.com/fampep/anifuse",
            version = "1.1.0",
            protocol = "1.1",
            deprecated = false,
        )
        val index = index(MIRROR, "2026-11-24", listing, reanime(deprecated = true))
        val merged = mergeStoreIndexes(listOf(index))
        assertEquals("site.anikitty.anifuse", merged.extensions.first().id)
        assertTrue(merged.extensions.first { it.id == "reanime" }.deprecated)
        assertFalse(merged.extensions.first { it.id == "site.anikitty.anifuse" }.deprecated)
    }

    @Test
    fun newerMonorepoCanReplaceARowAndAddOne() {
        val merged = mergeStoreIndexes(
            listOf(
                index(MIRROR, "2026-11-14", reanime(version = "1.0.0")),
                index(MONOREPO, "2026-11-15", reanime(version = "1.0.1"), hello()),
            ),
        )
        val reanime = merged.extensions.first { it.id == "reanime" }
        assertEquals("1.0.1", reanime.version)
        assertTrue(merged.extensions.any { it.id == "site.anikitty.example.hello" })
    }

    @Test
    fun anirexStaysOnTheListAndPlaysOnDevice() {
        val merged = mergeStoreIndexes(listOf(index(MIRROR, "2026-11-15", reanime())))
        val rex = merged.extensions.first { it.id == ANIREX_ID }
        assertEquals(ANIREX_SITE, rex.siteUrl)
        assertTrue(rex.playsOnDevice)
        assertFalse(isCatalogSite(rex))
        assertTrue(storeListingMatches(rex, "anirex.to"))
        assertEquals(
            StoreCardRole.OpenSite,
            storeCardRole(
                selected = false,
                deprecated = false,
                onDevice = false,
                bundled = false,
                sample = false,
                catalogSite = true,
            ),
        )
        assertEquals(
            StoreCardRole.Use,
            storeCardRole(
                selected = false,
                deprecated = false,
                onDevice = true,
                bundled = false,
                sample = false,
                catalogSite = true,
            ),
        )
    }

    private fun reanime(version: String = "1.0.0", deprecated: Boolean = false) = StoreListing(
        id = "reanime",
        name = "reanime",
        description = "Bundled reanime.cz resolver.",
        github = "https://github.com/fampep/anikitty-extensions/tree/main/providers/reanime",
        version = version,
        protocol = "1.1",
        iconUrl = "https://www.google.com/s2/favicons?domain=reanime.cz&sz=128",
        siteUrl = "https://reanime.cz",
        deprecated = deprecated,
    )

    private fun anipm() = StoreListing(
        id = "anipm",
        name = "ani.pm",
        description = "On-device ani.pm resolve. The first-party playlist plays on this phone.",
        github = "https://github.com/fampep/anikitty-extensions/tree/main/providers/anipm",    )

    private fun hello() = StoreListing(
        id = "site.anikitty.example.hello",
        name = "Hello HTTP",
        description = "example",
        github = "https://github.com/fampep/anikitty-extensions/tree/main/examples/hello-http",
        version = "1.1.1",
        protocol = "1.1",
    )

    private fun index(url: String, updatedAt: String, vararg listings: StoreListing) = StoreIndex(
        apiVersion = 1,
        updatedAt = updatedAt,
        extensions = listings.toList(),
        sourceUrl = url,
    )

    private companion object {
        const val MIRROR =
            "https://raw.githubusercontent.com/fampep/anikitty-extensions/main/store/index.json"
        const val MONOREPO =
            "https://raw.githubusercontent.com/fampep/anikitty/main/extensions/store/index.json"
    }
}
