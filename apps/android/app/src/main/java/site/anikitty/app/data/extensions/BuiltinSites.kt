// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.extensions

/**
 * Sites the store always knows, even when the remote index has not caught up.
 * [StoreListing.playsOnDevice] is false until a resolver for that site ships
 * in the app or the user installs an extension that can resolve it.
 */
internal fun builtinStoreListings(): List<StoreListing> = listOf(
    StoreListing(
        id = ANIREX_ID,
        name = "AniRex",
        description = "On-device anirex.to resolve. FlixCloud plays on this phone.",
        github = ANIREX_SITE,
        author = "anirex.to",
        featured = true,
        iconUrl = "https://www.google.com/s2/favicons?domain=anirex.to&sz=128",
        siteUrl = ANIREX_SITE,
        version = "site",
        protocol = "1.1",
        playsOnDevice = true,
    ),
    StoreListing(
        id = ANIMEHEAVEN_ID,
        name = "AnimeHeaven",
        description = "On-device animeheaven.me resolve. The gate file plays on this phone.",
        github = ANIMEHEAVEN_SITE,
        author = "animeheaven.me",
        featured = true,
        iconUrl = "https://www.google.com/s2/favicons?domain=animeheaven.me&sz=128",
        siteUrl = ANIMEHEAVEN_SITE,
        version = "site",
        protocol = "1.1",
        playsOnDevice = true,
    ),
    StoreListing(
        id = ONEKO_ID,
        name = "0neko",
        description = "On-device 0neko resolve. The playlist plays on this phone.",
        github = ONEKO_SITE,
        author = "0neko.cc",
        featured = true,
        iconUrl = "https://www.google.com/s2/favicons?domain=0neko.cc&sz=128",
        siteUrl = ONEKO_SITE,
        version = "site",
        protocol = "1.1",
        playsOnDevice = true,
    ),
)

internal const val ANIREX_ID = "anirex"
internal const val ANIREX_SITE = "https://anirex.to/"
internal const val ANIMEHEAVEN_ID = "animeheaven"
internal const val ANIMEHEAVEN_SITE = "https://animeheaven.me/"
internal const val ONEKO_ID = "oneko"
internal const val ONEKO_SITE = "https://beta.0neko.cc/"

internal fun isCatalogSite(listing: StoreListing): Boolean = !listing.playsOnDevice

internal fun storeListingMatches(listing: StoreListing, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return listing.name.contains(q, ignoreCase = true) ||
        listing.description.contains(q, ignoreCase = true) ||
        listing.author.contains(q, ignoreCase = true) ||
        listing.id.contains(q, ignoreCase = true) ||
        listing.siteUrl.orEmpty().contains(q, ignoreCase = true)
}

internal enum class StoreCardRole {
    Selected,
    Deprecated,
    Use,
    Install,
    OpenSite,
    Example,
}

/** What the store card's primary button does. Catalog sites open the origin. */
internal fun storeCardRole(
    selected: Boolean,
    deprecated: Boolean,
    onDevice: Boolean,
    bundled: Boolean,
    sample: Boolean,
    catalogSite: Boolean,
): StoreCardRole = when {
    selected -> StoreCardRole.Selected
    deprecated -> StoreCardRole.Deprecated
    sample && !onDevice && !bundled -> StoreCardRole.Example
    catalogSite && !onDevice && !bundled -> StoreCardRole.OpenSite
    onDevice || bundled -> StoreCardRole.Use
    else -> StoreCardRole.Install
}
