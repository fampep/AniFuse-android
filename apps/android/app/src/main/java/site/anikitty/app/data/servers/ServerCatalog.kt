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
import kotlinx.coroutines.withContext
import site.anikitty.app.data.anifuse.AnifuseApiStore
import site.anikitty.app.data.anifuse.AnifuseFeedCache
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
 * Stream sources = live on-device site extensions (Anikoto, Gotaku, …) plus
 * any community HTTP extensions the user installs. Playback races those
 * sites directly — the hosted AniFuse API is no longer in the chain.
 */
class ServerCatalog(context: Context, private val apiStore: AnifuseApiStore = AnifuseApiStore(context)) {
    private val prefs = context.getSharedPreferences("anikitty_servers", Context.MODE_PRIVATE)
    private val providerHealth = AnifuseFeedCache(java.io.File(context.filesDir, "anifuse-cache"))

    init {
        val hostDir = java.io.File(context.filesDir, "playback-hosts")
        PlaybackHostHealth.attach(hostDir)
    }
    private val resolveCache = ResolveCache(context)
    private val episodeAffinity = EpisodeHostAffinity(context)
    private val liveSources = LiveSourceStore(context)
    private val episodeArtCatalog = EpisodeArtCatalog.shared(context)
    private val megaPlayCrypto = MegaPlayCrypto(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val aniSkip = AniSkipClient()
    val extensions = ExtensionRepository(context)
    val installer = ExtensionInstaller(extensions)
    val storeClient = ExtensionStoreClient()

    private val siteResolvers: List<StreamResolver> = listOf(
        AnikotoServer(cache = resolveCache, crypto = megaPlayCrypto),
        GotakuServer(cache = resolveCache),
        ReanimeServer(cache = resolveCache),
        MkissaServer(cache = resolveCache),
        KaaServer(cache = resolveCache),
        AnimexServer(cache = resolveCache),
        XanimeServer(cache = resolveCache),
        AniNzServer(cache = resolveCache),
        AniPmServer(cache = resolveCache),
        GogoAnimeServer(cache = resolveCache),
        AnizoneServer(cache = resolveCache),
        AniwavesServer(cache = resolveCache, crypto = megaPlayCrypto),
        AnimeggServer(cache = resolveCache),
        AnigatoServer(cache = resolveCache),
        AniHostsServer(cache = resolveCache),
        OnekoServer(cache = resolveCache),
        AniRexServer(cache = resolveCache),
        AnimeHeavenServer(cache = resolveCache),
        TwoDHiveServer(cache = resolveCache),
    )
    private val bundled: List<StreamResolver> = siteResolvers

    /** Bundled site ids — live scrapers that ship with the APK. */
    val bundledSiteIds: Set<String> = bundled.map { it.server.id }.toSet()

    /** Full bundled site list (enabled or not) for Settings toggles. */
    val bundledServers: List<StreamServer> = bundled.map { it.server }

    /**
     * Nothing in the live site set is deprecated anymore.
     * Kept for store UI callers that still read the field.
     */
    val deprecatedBundledIds: Set<String> = emptySet()

    /** Optional hosted API id — kept for settings/migration only, not playback. */
    val anifuseExtensionId: String
        get() = ANIFUSE_EXTENSION_ID

    /** On-phone ids for the store UI. */
    val bundledIds: Set<String> = bundledSiteIds

    private val _resolvers = MutableStateFlow(buildResolvers(extensions.installed.value))
    val resolvers: StateFlow<List<StreamResolver>> = _resolvers.asStateFlow()

    private val _selectedId = MutableStateFlow(readSelectedId())
    val selectedId: StateFlow<String> = _selectedId.asStateFlow()

    val servers: List<StreamServer> get() = _resolvers.value.map { it.server }

    init {
        // Every live site + community install comes back into the race.
        // Restored bundled ids (e.g. ani.pm) must not stay disabled from an older cut.
        extensions.enableAll()
        extensions.ensureEnabled(bundledSiteIds)
        scope.launch {
            combine(extensions.installed, extensions.disabled) { installed, _ ->
                installed
            }.collect { installed ->
                _resolvers.value = buildResolvers(installed)
                ensureSelectionValid()
            }
        }
        // Retained so Settings can still show/self-host the optional API URL.
        // Playback no longer rebuilds when the endpoint changes.
        apiStore.baseUrl
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

    /**
     * Per-episode stills scraped from live sites (AniGato) and AniZip.
     * Used by the episode selector / details so thumbs are real frames,
     * not the series poster.
     */
    suspend fun episodeArt(anilistId: Int): Map<Int, SiteEpisodeArt> =
        withContext(Dispatchers.IO) { episodeArtCatalog.load(anilistId) }

    fun selectedResolver(): StreamResolver {
        val list = _resolvers.value
        return list.firstOrNull { it.server.id == _selectedId.value }
            ?: list.firstOrNull()
            ?: error("No stream extensions installed")
    }

    /**
     * Instant reopen: memory live/next lanes first, then the disk-backed copy
     * that survives process death. Empty means the caller should race CDNs.
     */
    suspend fun takeProbedLive(
        anilistId: Int,
        episode: Int,
        audio: String,
    ): List<PlayableSource> = takeProbedWarm(
        anilistId = anilistId,
        episode = episode,
        audio = audio,
        diskRecall = { liveSources.recall(anilistId, episode, audio) },
        onDiskMiss = { liveSources.forget(anilistId, episode, audio) },
    )

    /** AniList frontier jumped — drop every disk live recall for this title. */
    fun forgetLiveTitle(anilistId: Int) {
        liveSources.forgetTitle(anilistId)
    }

    /**
     * Resolve using the selected installed provider first, then automatically
     * try every other **enabled** provider until one returns sources.
     * A fallback plays this episode only. The provider the user picked stays selected.
     */
    suspend fun resolveWithFailover(
        anilistId: Int,
        episode: Int,
        audio: String = "sub",
        titleHint: String? = null,
        onAttempt: ((serverName: String, index: Int, total: Int) -> Unit)? = null,
        avoidHost: String? = null,
        warmLead: Boolean = false,
    ): ResolveFailoverResult {
        val list = _resolvers.value
        if (list.isEmpty()) error("No stream extensions installed")
        val preferredId = _selectedId.value
        val down = downProviderNames()
        val baseOrder = buildList {
            list.firstOrNull { it.server.id == preferredId }?.let { add(it) }
            list.filterNot { it.server.id == preferredId }.forEach { add(it) }
        }
        val sunk = sinkUnhealthy(
            ordered = baseOrder,
            down = down,
            id = { it.server.id },
            name = { it.server.name },
        )
        // Preferred stays first. Affinity for this episode rides second. Everyone
        // else is ordered by recent CDN health so the parallel window burns on
        // hosts that just played.
        val affinity = episodeAffinity.recall(anilistId, episode, audio)
        val boosted = boostAffinityProvider(sunk, affinity?.providerId) { it.server.id }
        val ordered = preferHealthyHosts(boosted) { resolver ->
            val hostScore = PlaybackHostHealth.scoreProvider(
                providerId = resolver.server.id,
                siteUrl = resolver.server.siteUrl,
            )
            val affinityHostBoost =
                if (affinity != null &&
                    resolver.server.id.equals(affinity.providerId, true) &&
                    affinity.host.isNotBlank()
                ) {
                    PlaybackHostHealth.score(affinity.host).coerceAtLeast(0)
                } else {
                    0
                }
            val affinityBoost =
                if (affinity != null && resolver.server.id.equals(affinity.providerId, true)) 250 else 0
            hostScore + affinityHostBoost + affinityBoost
        }
        val errors = java.util.Collections.synchronizedList(ArrayList<String>())
        val preferred = ordered.first()
        val preferredCold = preferred.server.id.lowercase() in down ||
            preferred.server.name.trim().lowercase() in down
        // Warm next-ep leads open the full pack with no head-start delay.
        val headStartMs = resolveHeadStartMs(
            anyDown = down.isNotEmpty(),
            preferredCold = preferredCold,
            warmLead = warmLead,
        )
        val raced = raceHeadStart(
            count = ordered.size,
            parallel = resolveParallel(ordered.size, warmLead),
            headStartMs = headStartMs,
            timeoutMs = RESOLVE_TIMEOUT_MS,
        ) { index ->
            val resolver = ordered[index]
            onAttempt?.invoke(resolver.server.name, index, ordered.size)
            val startedAt = android.os.SystemClock.elapsedRealtime()
            val sources = try {
                resolver.resolve(anilistId, episode, audio, titleHint)
            } catch (err: Exception) {
                if (err is CancellationException) throw err
                val msg = err.message?.takeIf { it.isNotBlank() } ?: err::class.java.simpleName
                errors += "${resolver.server.name}: $msg"
                rememberProviderDown(resolver, msg)
                null
            }
            val resolveMs = (android.os.SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
            when {
                sources.isNullOrEmpty() -> {
                    if (sources != null) {
                        // Soft miss — do not pulse the provider down for one empty title.
                        errors += "${resolver.server.name}: empty sources"
                    }
                    null
                }
                else -> {
                    val named = namedByServer(playbackAudioLanguage(sources, audio), resolver.server.name)
                    val accepted = sourcesAvoidingHost(named, avoidHost)
                    if (accepted.isEmpty()) {
                        errors += "${resolver.server.name}: same host"
                        null
                    } else {
                        Triple(resolver, accepted, resolveMs)
                    }
                }
            }
        }
        if (raced != null) {
            val (resolver, sources, resolveMs) = raced.second
            rememberProviderUp(resolver)
            val leadHost = playableHost(sources.firstOrNull()?.url.orEmpty())
            if (leadHost.isNotEmpty()) {
                PlaybackHostHealth.note(leadHost, ok = true, latencyMs = resolveMs)
                PlaybackHostHealth.noteProviderCdn(resolver.server.id, leadHost)
                // Prefer the lead CDN so Race dock LEAD · ms is a live HEAD sample.
                HostHealthProbe.kickRemembered(preferredHost = leadHost)
            }
            // Shadow / credits races pass avoidHost — never overwrite the playing live lane.
            val primaryRace = avoidHost.isNullOrBlank()
            if (primaryRace) {
                if (leadHost.isNotEmpty()) {
                    episodeAffinity.remember(
                        anilistId = anilistId,
                        episode = episode,
                        audio = audio,
                        providerId = resolver.server.id,
                        host = leadHost,
                    )
                }
                // Survive process death: next Play probes this before any race.
                PlayableWarmCache.put(
                    anilistId = anilistId,
                    episode = episode,
                    audio = audio,
                    sources = sources,
                    lane = PlayableWarmCache.LANE_LIVE,
                )
                liveSources.remember(anilistId, episode, audio, sources)
            }
            val usedFallback = resolver.server.id != preferredId
            return ResolveFailoverResult(
                sources = sources,
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
        if (bundled.any { it.server.id == id }) return
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
        // Live site scrapers first — every enabled bundled source races.
        val fromSites = bundled.filter { extensions.isEnabled(it.server.id) }
        val fromExt = installed
            .filter { extensions.isEnabled(it.id) }
            .filterNot {
                it.id == ANIFUSE_EXTENSION_ID ||
                    it.id in bundledSiteIds ||
                    isRetiredExtension(it.id, it.name, it.siteUrl)
            }
            .map { HttpExtensionResolver(it) }
        return fromSites + fromExt
    }

    private fun readSelectedId(): String {
        val defaultId = DEFAULT_SITE_ID
        val raw = prefs.getString(KEY_SELECTED, defaultId) ?: defaultId
        val migrated = when {
            raw == ANIFUSE_EXTENSION_ID -> defaultId
            raw in bundledSiteIds -> raw
            else -> FlixCrypto.migrateServerId(raw) ?: defaultId
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

    /** Soft-blocked provider ids/names from the last hard failures. */
    fun coldProviderKeys(): Set<String> = downProviderNames()

    private fun downProviderNames(): Set<String> =
        providerHealth.downNames() + providerHealth.pulseDownKeys()

    /** Soft-block providers that just hard-failed so the next race skips them first. */
    private fun rememberProviderDown(resolver: StreamResolver, message: String?) {
        if (!isHardProviderFailure(message)) return
        val keys = listOf(resolver.server.id, resolver.server.name)
        providerHealth.notePulse(keys, ok = false)
        val next = providerHealth.downNames() +
            resolver.server.id.lowercase() +
            resolver.server.name.trim().lowercase()
        providerHealth.writeDown(next)
    }

    private fun rememberProviderUp(resolver: StreamResolver) {
        providerHealth.notePulse(listOf(resolver.server.id, resolver.server.name), ok = true)
        val down = providerHealth.downNames()
        if (down.isEmpty()) return
        val cleared = down -
            resolver.server.id.lowercase() -
            resolver.server.name.trim().lowercase()
        if (cleared.size != down.size) providerHealth.writeDown(cleared)
    }

    private companion object {
        const val KEY_SELECTED = "selected_server_id"

        /** Default live site when nothing else is selected. */
        const val DEFAULT_SITE_ID = "anikoto"

        /** Legacy hosted API id — migrated away from selection. */
        const val ANIFUSE_EXTENSION_ID = "site.anikitty.anifuse"
    }
}
