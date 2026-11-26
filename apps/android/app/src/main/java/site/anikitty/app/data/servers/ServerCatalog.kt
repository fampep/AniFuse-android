// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import site.anikitty.app.data.anifuse.AnifuseApiStore
import site.anikitty.app.data.extensions.ExtensionInstaller
import site.anikitty.app.data.extensions.ExtensionManifest
import site.anikitty.app.data.extensions.ExtensionRepository
import site.anikitty.app.data.extensions.ExtensionStoreClient
import site.anikitty.app.data.extensions.HttpExtensionResolver
import site.anikitty.app.data.extensions.isRetiredExtension
import site.anikitty.app.data.skip.AniSkipClient
import site.anikitty.app.data.skip.AniSkipStamp
import site.anikitty.app.data.skip.SkipTimes
import site.anikitty.app.data.extensions.StoreIndex
import site.anikitty.app.data.extensions.StoreInstall
import site.anikitty.app.data.extensions.StoreListing
import site.anikitty.app.data.extensions.prepareStoreInstall

/**
 * Stream sources = the main AniFuse extension + user-installed HTTP extensions.
 * The deprecated on-device scrapers stay compiled in (skip times, latest
 * cards and audio availability still read them) but never play: playback
 * goes through AniFuse, which fronts every source in parallel.
 */
class ServerCatalog(context: Context, private val apiStore: AnifuseApiStore = AnifuseApiStore(context)) {
    private val prefs = context.getSharedPreferences("anikitty_servers", Context.MODE_PRIVATE)
    private val resolveCache = ResolveCache(context)
    private val megaPlayCrypto = MegaPlayCrypto(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val aniSkip = AniSkipClient()
    val extensions = ExtensionRepository(context)
    val installer = ExtensionInstaller(extensions)
    val storeClient = ExtensionStoreClient()

    private val bundled: List<StreamResolver> = listOf(
        AnikotoServer(cache = resolveCache, crypto = megaPlayCrypto),
        GotakuServer(cache = resolveCache),
        ReanimeServer(cache = resolveCache),
        MkissaServer(cache = resolveCache),
        KaaServer(cache = resolveCache),
        AnimexServer(cache = resolveCache),
        XanimeServer(cache = resolveCache),
        AniNzServer(cache = resolveCache),
        GogoAnimeServer(cache = resolveCache),
        AnizoneServer(cache = resolveCache),
        AniwavesServer(cache = resolveCache),
        AnimeggServer(cache = resolveCache),
        AnigatoServer(cache = resolveCache),
        AniHostsServer(cache = resolveCache),
    )

    /**
     * The on-device scrapers are deprecated: AniFuse fronts every source
     * now, so these stay out of playback and the store shows them as such.
     */
    val deprecatedBundledIds: Set<String> = bundled.map { it.server.id }.toSet()

    /** The main extension: the hosted AniFuse API fronting all scrapers. */
    private fun buildAnifuseResolver(): StreamResolver {
        val baseUrl = apiStore.baseUrl.value
        return HttpExtensionResolver(
            ExtensionManifest(
                apiVersion = ExtensionManifest.SUPPORTED_API,
                id = ANIFUSE_EXTENSION_ID,
                name = "AniFuse",
                version = "1.1.0",
                description = "One API for every anime source — parallel failover across 13 sites with captions and live health.",
                author = "fampep",
                homepage = "https://github.com/fampep/anifuse",
                baseUrl = baseUrl,
                resolvePath = ExtensionManifest.DEFAULT_RESOLVE_PATH,
                iconUrl = ANIFUSE_ICON_URL,
                siteUrl = baseUrl,
            ),
        )
    }

    /** On-phone ids for the store UI: the deprecated scrapers plus AniFuse. */
    val bundledIds: Set<String> = deprecatedBundledIds + ANIFUSE_EXTENSION_ID

    /** Store id of the main AniFuse extension (also listed in the store index). */
    val anifuseExtensionId: String
        get() = ANIFUSE_EXTENSION_ID

    private val _resolvers = MutableStateFlow(buildResolvers(extensions.installed.value))
    val resolvers: StateFlow<List<StreamResolver>> = _resolvers.asStateFlow()

    private val _selectedId = MutableStateFlow(readSelectedId())
    val selectedId: StateFlow<String> = _selectedId.asStateFlow()

    val servers: List<StreamServer> get() = _resolvers.value.map { it.server }

    init {
        scope.launch {
            // Rebuild whenever installed extensions or their enabled state
            // change, or the user points the app at another AniFuse instance.
            combine(extensions.installed, extensions.disabled) { installed, _ ->
                installed
            }.collect { installed ->
                _resolvers.value = buildResolvers(installed)
                ensureSelectionValid()
            }
        }
        scope.launch {
            apiStore.baseUrl.collect {
                _resolvers.value = buildResolvers(extensions.installed.value)
                ensureSelectionValid()
            }
        }
    }

    fun select(id: String) {
        if (_resolvers.value.none { it.server.id == id }) return
        prefs.edit().putString(KEY_SELECTED, id).apply()
        _selectedId.update { id }
    }

    /**
     * AniSkip openings and endings for this MAL episode. The player lines
     * them up with the stream that is playing.
     */
    suspend fun aniSkipStamps(malId: Int, episode: Int): List<AniSkipStamp> =
        aniSkip.load(malId, episode)

    /**
     * Intro and credits published on Anikoto's own episode page.
     */
    suspend fun anikotoSkipTimes(
        anilistId: Int,
        episode: Int,
        titleHint: String?,
    ): SkipTimes {
        val anikoto = bundled.filterIsInstance<AnikotoServer>().firstOrNull()
            ?: return SkipTimes()
        return anikoto.loadSkipTimes(anilistId, episode, titleHint)
    }

    /** Latest Sub/Dub cards scraped from Anikoto's home widgets. */
    suspend fun anikotoLatestUpdates(kind: String = "all"): List<AnikotoLatestItem> {
        val anikoto = bundled.filterIsInstance<AnikotoServer>().firstOrNull()
            ?: return emptyList()
        return anikoto.latestUpdates(kind)
    }

    /** True Sub/Dub episode counts hosted on Anikoto for one title. */
    suspend fun anikotoAudioAvailability(
        anilistId: Int,
        titleHint: String?,
    ): AnikotoAudioAvailability? {
        val anikoto = bundled.filterIsInstance<AnikotoServer>().firstOrNull()
            ?: return null
        return anikoto.audioAvailability(anilistId, titleHint)
    }

    fun selectedResolver(): StreamResolver {
        val list = _resolvers.value
        return list.firstOrNull { it.server.id == _selectedId.value }
            ?: list.firstOrNull()
            ?: error("No stream extensions installed")
    }

    /**
     * Resolve using the selected installed provider first, then automatically
     * try every other **installed** provider until one returns sources.
     * A fallback plays this episode only. The provider the user picked stays selected.
     */
    suspend fun resolveWithFailover(
        anilistId: Int,
        episode: Int,
        audio: String = "sub",
        titleHint: String? = null,
        onAttempt: ((serverName: String, index: Int, total: Int) -> Unit)? = null,
    ): ResolveFailoverResult {
        val list = _resolvers.value
        if (list.isEmpty()) error("No stream extensions installed")
        val preferredId = _selectedId.value
        val ordered = buildList {
            list.firstOrNull { it.server.id == preferredId }?.let { add(it) }
            list.filterNot { it.server.id == preferredId }.forEach { add(it) }
        }
        val errors = java.util.Collections.synchronizedList(ArrayList<String>())
        val raced = raceHeadStart(
            count = ordered.size,
            parallel = 3,
            headStartMs = RESOLVE_HEAD_START_MS,
            timeoutMs = RESOLVE_TIMEOUT_MS,
        ) { index ->
            val resolver = ordered[index]
            onAttempt?.invoke(resolver.server.name, index, ordered.size)
            val sources = try {
                resolver.resolve(anilistId, episode, audio, titleHint)
            } catch (err: Exception) {
                if (err is CancellationException) throw err
                val msg = err.message?.takeIf { it.isNotBlank() } ?: err::class.java.simpleName
                errors += "${resolver.server.name}: $msg"
                null
            }
            when {
                sources.isNullOrEmpty() -> {
                    if (sources != null) errors += "${resolver.server.name}: empty sources"
                    null
                }
                else -> resolver to sources
            }
        }
        if (raced != null) {
            val (resolver, sources) = raced.second
            val usedFallback = resolver.server.id != preferredId
            val named = namedByServer(playbackAudioLanguage(sources, audio), resolver.server.name)
            return ResolveFailoverResult(
                sources = named,
                server = resolver.server,
                usedFallback = usedFallback,
                attempted = errors + "${resolver.server.name}: ok",
            )
        }
        error(
            buildString {
                append("All installed providers failed")
                if (errors.isNotEmpty()) {
                    append(" — ")
                    append(errors.joinToString(" · "))
                }
            },
        )
    }

    fun uninstallExtension(id: String) {
        if (id == ANIFUSE_EXTENSION_ID || bundled.any { it.server.id == id }) return
        extensions.remove(id)
        ensureSelectionValid()
    }

    suspend fun installFromGithub(url: String): ExtensionManifest =
        installer.installFromGithub(url)

    suspend fun installStoreListing(listing: StoreListing): ExtensionManifest {
        return when (val plan = prepareStoreInstall(listing, bundledIds)) {
            is StoreInstall.OnDevice ->
                error("${listing.name} ships with AniFuse. It is already on this phone.")
            is StoreInstall.Fetch -> installer.installFromUrls(plan.urls, listing.github)
        }
    }

    suspend fun fetchStore(): StoreIndex = storeClient.fetchIndex()

    /** Newer versions of installed community extensions, id → version. */
    suspend fun extensionUpdates(): Map<String, String> = installer.availableUpdates()

    suspend fun updateExtension(id: String): ExtensionManifest = installer.update(id)

    private fun buildResolvers(installed: List<ExtensionManifest>): List<StreamResolver> {
        // Disabled extensions stay installed but leave the failover chain.
        val fromExt = installed
            .filter { extensions.isEnabled(it.id) }
            .map { HttpExtensionResolver(it) }
        val extras = fromExt.filterNot {
            it.server.id == ANIFUSE_EXTENSION_ID ||
                it.server.id in deprecatedBundledIds ||
                isRetiredExtension(it.server.id, it.server.name)
        }
        // AniFuse is the main extension and the only bundled playback
        // resolver; the deprecated on-device scrapers stay out of failover.
        return listOf(buildAnifuseResolver()) + extras
    }

    private fun readSelectedId(): String {
        val raw = prefs.getString(KEY_SELECTED, ANIFUSE_EXTENSION_ID) ?: ANIFUSE_EXTENSION_ID
        val migrated = when {
            raw == ANIFUSE_EXTENSION_ID -> raw
            raw in deprecatedBundledIds -> ANIFUSE_EXTENSION_ID
            else -> FlixCrypto.migrateServerId(raw) ?: ANIFUSE_EXTENSION_ID
        }
        if (migrated != raw) prefs.edit().putString(KEY_SELECTED, migrated).apply()
        return migrated
    }

    private fun ensureSelectionValid() {
        val list = _resolvers.value
        if (list.isEmpty()) return
        if (list.none { it.server.id == _selectedId.value }) {
            select(list.first().server.id)
        }
    }

    private companion object {
        const val KEY_SELECTED = "selected_server_id"
        /** Preferred resolver's head start before the next providers join. */
        const val RESOLVE_HEAD_START_MS = 1_500L

        /** Upper bound for one provider's resolve before that racer is dropped. */
        const val RESOLVE_TIMEOUT_MS = 8_000L

        /** The main AniFuse extension bundled with the app. */
        const val ANIFUSE_EXTENSION_ID = "site.anikitty.anifuse"
        const val ANIFUSE_ICON_URL =
            "https://raw.githubusercontent.com/fampep/anifuse/main/docs/brand/anifuse-icon-512.png"
    }
}
