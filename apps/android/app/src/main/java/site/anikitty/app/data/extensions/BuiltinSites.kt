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
        id = ANIVAULT_ID,
        name = "AniVault",
        description = "AniVault's own sources. The file plays on this phone.",
        github = ANIVAULT_SITE,
        author = "anivault.tv",
        featured = true,
        iconUrl = "https://www.google.com/s2/favicons?domain=anivault.tv&sz=128",
        siteUrl = ANIVAULT_SITE,
        version = "site",
        protocol = "1.1",
        playsOnDevice = true,
    ),
)

internal const val ANIVAULT_ID = "anivault"
internal const val ANIVAULT_SITE = "https://anivault.tv/"

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
