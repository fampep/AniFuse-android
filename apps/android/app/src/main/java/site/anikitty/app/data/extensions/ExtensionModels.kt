// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.extensions

/**
 * Installed / store-listed AniFuse stream extension (apiVersion 1).
 * Auth VPS + Media3 stay in the APK; extensions only resolve playable URLs.
 */
data class ExtensionManifest(
    val apiVersion: Int,
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val author: String = "",
    val homepage: String? = null,
    val baseUrl: String,
    val resolvePath: String = DEFAULT_RESOLVE_PATH,
    /** Required HTTPS icon of the site this extension scrapes / fronts. */
    val iconUrl: String,
    /** Public site origin shown in the Store (usually the scraped host). */
    val siteUrl: String? = null,
    val nsfw: Boolean = false,
    /** GitHub repo URL this was installed from (if any). */
    val sourceGithub: String? = null,
    /** Raw manifest URL used for updates. */
    val manifestUrl: String? = null,
) {
    fun resolveEndpoint(): String = baseUrl.trimEnd('/') + "/" + resolvePath.trimStart('/')

    companion object {
        const val SUPPORTED_API = 1
        const val DEFAULT_RESOLVE_PATH = "/v1/resolve"
    }
}

data class StoreListing(
    val id: String,
    val name: String,
    val description: String,
    val github: String,
    val author: String = "",
    val featured: Boolean = false,
    val manifestPath: String? = null,
    /** Site icon shown in the Store grid (required for new listings). */
    val iconUrl: String? = null,
    val siteUrl: String? = null,
    /** Extension semver from the store index. Manifest `version` is what an install updates to. */
    val version: String? = null,
    /** Protocol document version, such as "1.1". Manifest `apiVersion` stays 1. */
    val protocol: String? = null,
    /**
     * Deprecated / retired listings stay visible for reference but leave
     * the playback race. Live bundled sites are never marked this way.
     */
    val deprecated: Boolean = false,
    /**
     * False when the store knows the site but this phone has no resolver for it.
     * The card opens the site. It does not join the playback race.
     */
    val playsOnDevice: Boolean = true,
)

/**
 * Settlar and Yozora are retired aliases.
 * Anime Nexus and Miruro are removed from the app.
 * The only live ani.pm card is the bundled [anipm] resolver — every other
 * ani.pm / anipm.pk listing (community mirrors, old settlar packs) is retired
 * so the Store never shows two ani.pm extensions.
 */
internal fun isRetiredExtension(id: String, name: String = "", siteUrl: String? = null): Boolean {
    fun norm(raw: String) = raw.trim().lowercase().replace(".", "").replace(" ", "").replace("-", "").replace("_", "")
    val key = norm(id)
    if (key == "settlar" || key == "yozora" || key == "animenexus" || key == "miruro") {
        return true
    }
    val label = norm(name)
    if (label == "settlar" || label == "yozora" ||
        label == "animenexus" || label == "miruro"
    ) {
        return true
    }
    // One ani.pm only — the bundled id. Drop every other pack for that site.
    if (key != "anipm" && isAniPmAlias(key, label, siteUrl)) return true
    return false
}

/** True when a listing is clearly ani.pm / anipm.pk but not the bundled id. */
internal fun isAniPmAlias(idNorm: String, nameNorm: String, siteUrl: String?): Boolean {
    if (nameNorm == "anipm" || nameNorm == "anipmpk") return true
    if (idNorm == "anipmpk" || idNorm.endsWith("anipm") || idNorm.contains("anipmpk")) return true
    val host = siteUrl
        ?.trim()
        ?.lowercase()
        ?.removePrefix("https://")
        ?.removePrefix("http://")
        ?.substringBefore('/')
        ?.substringBefore(':')
        ?.removePrefix("www.")
        .orEmpty()
    return host == "ani.pm" || host == "anipm.pk"
}

data class StoreIndex(
    val apiVersion: Int,
    val updatedAt: String?,
    val extensions: List<StoreListing>,
    /** Which index URL served this payload (for Store status chrome). */
    val sourceUrl: String? = null,
    /** Every index that contributed listings, extensions repo first when present. */
    val sourceUrls: List<String> = emptyList(),
)
