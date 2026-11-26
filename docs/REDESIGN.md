# AniFuse Redesign — Work Log

Ground-up redesign and AniFuse resolver API integration for the Android app
(`apps/android`, package `site.anikitty.app`, Kotlin + Jetpack Compose + Media3).
Work was done directly on the `main` branch of `anikitty/AniFuse` in logical
commit groups.

## Commit groups

| Commit | Area |
| --- | --- |
| `feat(api)` | First-class AniFuse resolver API client (`data/anifuse`) |
| `feat(ui)` | Five-tab glass shell, Discover destination, API health board |
| `feat(extensions)` | Extension enable/disable, Player settings, About, API tests |
| `feat(player)` | Source menu shows provider/quality/audio per stream |
| `feat(settings)` | Hero rotation toggle, image cache management |
| `docs` | Redesign work log and refreshed README capabilities |
| `chore(providers)` | Health board atmosphere background and refresh-failure polish |

## What changed

### Resolver API (previously resolve-only via the extension protocol)

- **`data/anifuse/AnifuseApiStore`** — configurable base URL (defaults to the
  hosted demo `https://143-246-203-212.sslip.io`), persisted, live `StateFlow`.
  Changing it in Settings takes effect immediately — `ServerCatalog` rebuilds
  the bundled AniFuse resolver on the new endpoint.
- **`data/anifuse/AnifuseApi`** — native Kotlin/OkHttp client for `/healthz`,
  `/v1/providers`, and `/v1/latest` with TTL caches mirroring the server's own
  cache, structured errors, and no main-thread work. `/v1/resolve` stays on
  the existing extension-protocol resolver (headers, subtitles, quality,
  per-source skip all already flow into Media3).
- **`data/anifuse/AnifuseModels`** — tolerant JSON models; unknown or missing
  optional fields never crash deserialization.
- **`data/anifuse/AnifuseLatestMapper`** — maps the aggregated latest feed
  onto AniList titles with exact-title matching and title+episode dedupe.

### Navigation & screens

- Bottom dock is now **Home · Discover · Search · Library · Profile**; the
  avatar marks Profile. Library is the renamed My Space; the schedule lives
  inside Discover.
- **Discover** (new): schedule + surprise-me doors, genre chips, trending,
  most popular, top rated, hidden gems (labeled AniFuse heuristic with a
  minimum-sample rule), movies, upcoming, this season.
- **AniFuse API settings** (Settings → AniFuse API): editable endpoint with
  HTTPS warning, reset-to-demo, live connection test, and the new
  **Provider health board** (status, success rate, attempts, cooldowns,
  refreshed-at) fed by `/v1/providers`.
- **Player settings** (Settings → Player): default Sub/Dub, playback speed,
  autoplay next, auto skip intro/outro, volume boost — written to the exact
  preference keys the player reads.
- Extensions can be **disabled without uninstalling**; disabled ones leave
  the failover chain and stay listed with a re-enable switch.
- Player Quality page separates **Resolution** from **Sources**, labeling
  each stream with provider, resolution and audio as actually reported.

### Homepage

- Latest Episodes now comes from the resolver API's aggregated `/v1/latest`
  (all providers) instead of the deprecated on-device Anikoto scrape.
- New **Most popular** row (AniList popularity), with show/hide + reorder in
  Settings → Home layout.
- Hero carousel rotation is now optional (Settings → Home layout).

### Design system

- **Dark glass vs AMOLED black** theme modes (Settings → Appearance): glass
  mode adds a subtle page atmosphere gradient and lifted surfaces; AMOLED
  keeps the original flat true-black look. Accent palette unchanged and still
  settings-driven.

## Preserved

- Package id `site.anikitty.app`, deep links, signing config, Room schema
  (no migration needed — no schema change), watch history, AniList OAuth and
  sync, extension protocol compatibility, Media3 playback (incl. required
  headers, HLS/MP4, captions, source fallback), downloads, Chromecast, PiP,
  TV shell, TalkBack labels, demo mode.

## Monochrome pass (AniFuse-android)

The phone UI is locked to the black-and-white AniFuse palette (`#080808` page, `#151515` cards, `#FFFFFF` selected controls, `#292929` borders). Colourful accent presets no longer paint chrome. Home, Discover, title, library, profile, and the notification centre follow the layout in the product spec while catalog, playback, AniList, downloads, and the extension resolver stay on their existing data paths.

## Build & test status

- `./gradlew :app:lintDebug` passes.
- `./gradlew :app:assembleDebug` and `:app:assembleRelease` pass.
  Outputs: `app/build/outputs/apk/debug/app-debug.apk` and
  `app/build/outputs/apk/release/app-release.apk` (release is signed with the
  debug key when `keystore.properties` is absent, which matches the Gradle
  fallback).
- `./gradlew :app:testDebugUnitTest`: 335 tests pass. Two live store tests
  (`ExtensionInstallLiveIndexTest`) fail because the pinned
  `fampep/anikitty-extensions` index returns HTTP 404. That URL is unchanged
  by the redesign.
- Not covered here: a physical device or emulator pass for gestures, Cast,
  picture-in-picture, and TalkBack.
