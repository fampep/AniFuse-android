// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.anilist

import org.json.JSONArray
import org.json.JSONObject

/** How many title searches share one GraphQL document. */
internal const val ANILIST_SEARCH_CHUNK = 6

/** How many ids share one brief media lookup. */
internal const val ANILIST_BRIEF_CHUNK = 25

internal fun chunkedList(items: List<String>, size: Int): List<List<String>> {
    if (items.isEmpty()) return emptyList()
    val width = size.coerceAtLeast(1)
    return items.chunked(width)
}

/**
 * One document, one alias per title. Fields stay small so six aliases remain
 * under AniList's complexity cap.
 */
internal fun searchManyQuery(titles: List<String>): Pair<String, JSONObject> {
    val decls = titles.indices.joinToString(", ") { "\$s$it: String" }
    val aliases = titles.indices.joinToString("\n") { index ->
        """
        s$index: Page(page: 1, perPage: 5) {
          media(type: ANIME, isAdult: false, search: ${'$'}s$index) {
            id
            idMal
            title { romaji english native }
            synonyms
            coverImage { large }
            format
            episodes
            averageScore
            genres
          }
        }
        """.trimIndent()
    }
    val variables = JSONObject()
    titles.forEachIndexed { index, title -> variables.put("s$index", title) }
    val query = "query ($decls) {\n$aliases\n}"
    return query to variables
}

/** Alias key to the media array. Missing aliases are listed separately. */
internal data class SearchAliasParse(
    val hits: Map<String, JSONArray>,
    val missing: List<String>,
)

internal fun parseSearchAliases(body: String, titles: List<String>): SearchAliasParse {
    val root = JSONObject(body)
    val data = root.optJSONObject("data")
    val hits = LinkedHashMap<String, JSONArray>()
    val missing = ArrayList<String>()
    titles.forEachIndexed { index, title ->
        val media = data?.optJSONObject("s$index")?.optJSONArray("media")
        if (media == null) missing += title else hits[title] = media
    }
    return SearchAliasParse(hits, missing)
}

/** id to released episode count. Airing shows use next episode minus one. */
internal fun releasedCountFromBrief(item: JSONObject): Pair<Int, Int>? {
    val id = item.optInt("id", 0)
    if (id <= 0) return null
    val listed = item.optInt("episodes", 0).takeIf { it > 0 } ?: 0
    val next = item.optJSONObject("nextAiringEpisode")?.optInt("episode", 0) ?: 0
    val aired = (next - 1).takeIf { it > 0 } ?: 0
    val count = maxOf(listed, aired)
    if (count <= 0) return null
    return id to count
}

internal fun parseBriefCounts(body: String): Map<Int, Int> {
    val media = JSONObject(body)
        .optJSONObject("data")
        ?.optJSONObject("Page")
        ?.optJSONArray("media")
        ?: return emptyMap()
    return buildMap {
        for (i in 0 until media.length()) {
            val row = media.optJSONObject(i) ?: continue
            val parsed = releasedCountFromBrief(row) ?: continue
            put(parsed.first, parsed.second)
        }
    }
}

/** Drop account-specific fields before a detail document is shared on disk. */
internal fun publicCatalogBody(body: String): String {
    val root = try {
        JSONObject(body)
    } catch (_: Exception) {
        return body
    }
    val media = root.optJSONObject("data")?.optJSONObject("Media") ?: return body
    media.remove("mediaListEntry")
    media.remove("isFavourite")
    return root.toString()
}
