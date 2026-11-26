// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import site.anikitty.app.cast.CastStartup
import site.anikitty.app.data.anifuse.AnifuseApi
import site.anikitty.app.data.anifuse.AnifuseApiStore
import site.anikitty.app.data.anilist.AniListAuthStore
import site.anikitty.app.data.anilist.AniListClient
import site.anikitty.app.data.anilist.AniListSync
import site.anikitty.app.data.auth.SavedPassphraseStore
import site.anikitty.app.data.auth.SessionStore
import site.anikitty.app.data.local.AppDatabase
import site.anikitty.app.data.server.DedicatedApiClient
import site.anikitty.app.data.server.ServerConfig
import site.anikitty.app.data.servers.ServerCatalog
import site.anikitty.app.data.servers.SubtitleLibrary

class AnikittyApp : Application(), ImageLoaderFactory {
    lateinit var database: AppDatabase
        private set
    lateinit var aniList: AniListClient
        private set
    lateinit var aniListAuth: AniListAuthStore
        private set
    lateinit var aniListSync: AniListSync
        private set
    lateinit var streamServers: ServerCatalog
        private set
    lateinit var anifuseStore: AnifuseApiStore
        private set
    lateinit var anifuseApi: AnifuseApi
        private set
    lateinit var serverConfig: ServerConfig
        private set
    lateinit var session: SessionStore
        private set
    lateinit var savedPassphrase: SavedPassphraseStore
        private set
    lateinit var api: DedicatedApiClient
        private set

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024L * 1024L)
                    .build()
            }
            .allowHardware(true)
            .crossfade(false)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Cast talks to Play services and is not needed for the first frame.
        CastStartup.start(this)
        database = AppDatabase.create(this)
        aniList = AniListClient(this)
        aniListAuth = AniListAuthStore(this)
        aniListSync = AniListSync(
            aniList,
            aniListAuth,
            database.watchHistoryDao(),
            database.pendingAniListPushDao(),
        )
        anifuseStore = AnifuseApiStore(this)
        anifuseApi = AnifuseApi(anifuseStore, java.io.File(filesDir, "anifuse-cache"))
        streamServers = ServerCatalog(this, anifuseStore)
        SubtitleLibrary.install(this)
        serverConfig = ServerConfig(this)
        session = SessionStore(this)
        savedPassphrase = SavedPassphraseStore(this)
        api = DedicatedApiClient(
            baseUrlProvider = { serverConfig.baseUrl },
            tokenProvider = { session.token },
        )
    }
}
