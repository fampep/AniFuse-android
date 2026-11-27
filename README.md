<div align="center">
  <img src="docs/brand/anikitty-icon-512.png" alt="AniFuse" width="96" height="96">

  <h1>AniFuse</h1>

  <p><strong>Your AniList. On the phone. Streams from live sites.</strong><br>
  Monochrome glass chrome. Colour stays in the artwork. Progress stays with you.</p>

  <p>
    <a href="https://github.com/fampep/AniFuse-android/releases/latest"><img src="https://img.shields.io/github/v/release/fampep/AniFuse-android?display_name=tag&style=for-the-badge&label=stable&labelColor=0a0a0a&color=f5f5f5" alt="Latest stable release"></a>
    <a href="https://github.com/fampep/AniFuse-android/releases"><img src="https://img.shields.io/github/v/release/fampep/AniFuse-android?include_prereleases&filter=1%5C.&display_name=tag&style=for-the-badge&label=dev&labelColor=0a0a0a&color=f5f5f5" alt="Latest dev prerelease"></a>
    <a href="https://github.com/fampep/AniFuse-android/releases"><img src="https://img.shields.io/github/downloads/fampep/AniFuse-android/total?style=for-the-badge&label=downloads&labelColor=0a0a0a&color=f5f5f5" alt="Downloads"></a>
    <a href="https://github.com/fampep/AniFuse-android/blob/main/LICENSE"><img src="https://img.shields.io/badge/license-Apache_2.0-f5f5f5?labelColor=0a0a0a&style=for-the-badge" alt="Apache License 2.0"></a>
    <img src="https://img.shields.io/badge/Android-8%2B-f5f5f5?labelColor=0a0a0a&style=for-the-badge" alt="Android 8 or newer">
  </p>

  <p>
    <a href="https://github.com/fampep/AniFuse-android/releases/latest"><img src="https://img.shields.io/badge/download-stable-f5f5f5?style=for-the-badge&labelColor=0a0a0a" alt="Download the latest stable release"></a>
    <a href="https://github.com/fampep/AniFuse-android/releases/download/dev/AniFuse_dev.apk"><img src="https://img.shields.io/badge/download-dev-f5f5f5?style=for-the-badge&labelColor=0a0a0a" alt="Download the rolling dev build"></a>
  </p>

  <p>
    <a href="https://ko-fi.com/E6C820K9H8"><img height="36" src="https://ko-fi.com/img/githubbutton_sm.svg" alt="Support AniFuse on Ko-fi"></a>
  </p>

  <p>
    <a href="https://t.me/Anikittymoe">Telegram</a>
    ·
    <a href="https://github.com/fampep/AniFuse-android/issues">Issues</a>
    ·
    <a href="CONTRIBUTING.md">Contributing</a>
  </p>
</div>

<p align="center">
  <img src="docs/screenshots/home.png" alt="AniFuse Home on Pixel 8" width="220">
  &nbsp;
  <img src="docs/screenshots/title.png" alt="AniFuse Title page on Pixel 8" width="220">
  &nbsp;
  <img src="docs/screenshots/player.png" alt="AniFuse Player on Pixel 8" width="220">
</p>

<p align="center">
  <img src="docs/screenshots/library.png" alt="AniFuse Library on Pixel 8" width="220">
  &nbsp;
  <img src="docs/screenshots/profile.png" alt="AniFuse Profile on Pixel 8" width="220">
</p>

> [!WARNING]
> **Unofficial.** AniFuse is not affiliated with AniList, any studio, or any streaming site.
>
> **This project hosts no video.** A stream plays only when an extension returns a URL. The app does not control that site. The site may show ads or send you somewhere that is not safe.
>
> **You are responsible** for the laws where you are, and for the terms of every site an extension opens.
>
> **No warranty. Use at your own risk.** Install APKs only from the [releases of this repository](https://github.com/fampep/AniFuse-android/releases). A file from anywhere else is not this project.

---

## What it is

AniFuse is a **local-first Android client** (Kotlin, Jetpack Compose, Media3).

| | |
| --- | --- |
| Package | `site.anikitty.app` |
| Deep link | `anikitty://` (kept so existing installs update in place) |
| Account | AniList on device — there is no AniFuse account |
| Streams | Live site extensions on the phone (bundled + store). Optional self-hosted API: [fampep/anifuse](https://github.com/fampep/anifuse) |

Black-and-white chrome. Anime artwork carries the colour. The floating glass dock is always one thumb away from Home, Discover, Search, Library, and Profile.

---

## Install

**Android 8 or newer.** Open a release, download the APK, allow install from this source if Android asks.

| Channel | Get it | Notes |
| --- | --- | --- |
| **Stable** | [Latest stable](https://github.com/fampep/AniFuse-android/releases/latest) | Everyday build. Filename is `AniFuse_<version>.apk`. |
| **Dev** | [Rolling APK](https://github.com/fampep/AniFuse-android/releases/download/dev/AniFuse_dev.apk) or newest prerelease on [Releases](https://github.com/fampep/AniFuse-android/releases) | Latest push to `main`. Can break. |

Inside the app: **Profile → App** → Stable or Dev. A newer build on the channel you picked shows up there.

---

## Screens

Pixel 8 device frames of the current monochrome glass UI (night theme, public catalog). Capture notes: [`docs/screenshots/README.md`](docs/screenshots/README.md).

| Screen | What you get |
| --- | --- |
| **Home** | Spotlight hero, continue watching, shelves, floating glass dock |
| **Title** | Full-bleed art, glass actions, episode stills from live sites + AniZip |
| **Player** | Media3 — Source / Quality / Audio card, failover, PiP, Cast |
| **Library** | Week goal, AniList lists, continue row |
| **Profile** | AniList identity, stats, settings hub |

---

## Features

### Browse & library

- **Home** — cinematic picks, continue watching, latest sub/dub shelves
- **Discover** — genre, year, season, format, status, score
- **Search** — AniList search
- **Library** — watching / plan / completed, downloads, week goal
- **Profile** — AniList stats, look, player, extensions, builds

### Playback

- Sub and dub, auto next, skip intro/outro when timings exist
- Adaptive quality, instant source failover when a CDN dies
- Picture-in-Picture and Chromecast when a route is available
- Progress saved on the phone and synced to AniList when signed in

### Look

- Black & white chrome · floating glass dock · Fluent icon set
- Artwork and stills carry colour; AMOLED look available in settings

### Sources

Bundled live-site resolvers (Anikoto, AniGato, ani.pm / AniPm, AniNz, …) race with health-aware failover. Community extensions install from the in-app store or a manifest link and can be toggled one by one. The app plays URLs — it does not upload episodes.

---

## Releases

Names follow [uBlock Origin](https://github.com/gorhill/uBlock/releases). Tag and title are the version. No `v` prefix.

| Channel | Name | Example | When |
| --- | --- | --- | --- |
| Stable | `x.y.z` | `1.2.13` | Manual: Release workflow with **stable**, or a commit message containing `[stable]` |
| Dev / alpha | `x.y.zbN` | `1.2.13b0` | Every other push to `main`. Prerelease. `b` counts up from `0` |

After stable `1.2.12`, next dev builds are `1.2.13b0`, `1.2.13b1`, … Cutting stable publishes `1.2.13`, then `main` moves to `1.2.14b0`.

Older builds used `1.2.12-dev.28` and a `v` on the tag. Those still install. Each numbered release attaches `AniFuse_<version>.apk`, signed with the same key so it installs over previous builds. Version name and code live in [`VERSION`](VERSION).

The floating `dev` tag is only a moving download — not a version number.

---

## Extensions

| | |
| --- | --- |
| Author guide | [`extensions/GUIDE.md`](extensions/GUIDE.md) |
| Protocol | [`extensions/spec/PROTOCOL.md`](extensions/spec/PROTOCOL.md) |
| Store | [`fampep/anikitty-extensions`](https://github.com/fampep/anikitty-extensions) · [`store/index.json`](https://github.com/fampep/anikitty-extensions/blob/main/store/index.json) |
| Disclaimer | [`extensions/DISCLAIMER.md`](extensions/DISCLAIMER.md) |

A link in the store is not fampep running that server. Community extensions stay with their authors.

---

## Build

```bash
cd apps/android
./gradlew :app:assembleRelease
```

Release APK: `apps/android/app/build/outputs/apk/release/`.

Pull requests run unit tests, a debug APK, and lint. Pushes to `main` re-run tests and publish the release APK.

---

## License

Copyright 2026 fampep · [Apache License 2.0](LICENSE)

This product uses the TMDB API but is not endorsed or certified by TMDB.

Questions and bugs → [GitHub issues](https://github.com/fampep/AniFuse-android/issues). Chat → [Telegram](https://t.me/Anikittymoe). Support → [Ko-fi](https://ko-fi.com/E6C820K9H8).
