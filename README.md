<div align="center">
  <img src="docs/brand/anikitty-icon-512.png" alt="AniFuse" width="96" height="96">

  <h1>AniFuse</h1>

  <p><strong>Your AniList, on the phone.</strong><br>
  Browse and sign in with AniList. Watch on the device. Progress stays with you.</p>

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
  </p>
</div>

> [!WARNING]
> **Unofficial.** AniFuse is not affiliated with AniList, any studio, or any streaming site.
>
> **This project hosts no video.** A stream plays only when an extension returns a URL. The app does not control that site. The site may show ads or send you somewhere that is not safe.
>
> **You are responsible** for the laws where you are, and for the terms of every site an extension opens.
>
> **No warranty. Use at your own risk.** Install APKs only from the [releases of this repository](https://github.com/fampep/AniFuse-android/releases). A file from anywhere else is not this project.

AniFuse is the Android client. Package id `site.anikitty.app` and the `anikitty://` deep link stay as they are so an existing install updates in place. The self-hosted resolver is [fampep/anifuse](https://github.com/fampep/anifuse).

## Install

Android 8 or newer. Open the release, download the APK, and allow install from this source if Android asks.

1. **Stable** — [latest stable release](https://github.com/fampep/AniFuse-android/releases/latest). Everyday build. The file is `AniFuse_1.2.13.apk` (the version in the name matches the release).
2. **Dev** — [rolling APK](https://github.com/fampep/AniFuse-android/releases/download/dev/AniFuse_dev.apk), or the newest prerelease on the [releases page](https://github.com/fampep/AniFuse-android/releases). This is the build from the latest push to `main`. It can break.

The same choice is inside the app under **Profile → App**: Stable or Dev. A newer build on the channel you picked shows up there.

## Releases

Names follow [uBlock Origin](https://github.com/gorhill/uBlock/releases). The GitHub tag and the release title are the version. There is no `v` prefix.

| Channel | Name | Example | When it is published |
| --- | --- | --- | --- |
| Stable | `x.y.z` | `1.2.13` | Manual. Run the Release workflow with **stable** checked, or push a commit whose message contains `[stable]`. |
| Dev / alpha | `x.y.zbN` | `1.2.13b0`, `1.2.13b1` | Every other push to `main`. `b` starts at `0` and counts up. Marked as a prerelease. |

After stable `1.2.12`, the next dev builds are `1.2.13b0`, `1.2.13b1`, and so on. Cutting stable on that line publishes `1.2.13`, then `main` moves to `1.2.14b0`.

Older builds used `1.2.12-dev.28` and a `v` on the tag. Those still install. A phone on `1.2.12-dev.28` treats `1.2.12b29` as newer, and `1.2.12` as newer than either. The floating `dev` tag is only a moving download. It is not a version.

Each numbered release attaches `AniFuse_<version>.apk`, signed with the same key as the previous builds so it installs over them. Version name and version code live in [`VERSION`](VERSION).

## The app

Local-first Android app: Kotlin, Jetpack Compose, Media3. Sign-in is AniList on the device. There is no AniFuse account.

| | |
| --- | --- |
| **Home** | Cinematic picks, continue watching, and the shelves you keep |
| **Discover** | Genre, year, season, format, status, and score filters |
| **Search** | AniList search |
| **Library** | Watching, plan to watch, completed, and downloads |
| **Profile** | AniList stats, app builds, and settings |
| **Player** | Sub and dub, skip intro and outro when the source has timings, speed, picture-in-picture, and Chromecast when a TV is available |
| **Progress** | Saved on the phone, and synced to AniList when you are signed in |
| **Look** | Black and white chrome. Colour stays in the artwork |

Streams go through the **AniFuse resolver API**. It asks the healthy providers and returns ranked URLs with the headers those CDNs expect. The hosted demo is the default. **Settings → AniFuse API** points the app at your own instance and shows provider health. If one source fails mid-episode, the player tries the next source and keeps the playhead. Community extensions install from the in-app store or a manifest link and can be turned off one by one. The app plays URLs. It does not upload episodes.

## Extensions

| | |
| --- | --- |
| Author guide | [`extensions/GUIDE.md`](extensions/GUIDE.md) |
| Protocol | [`extensions/spec/PROTOCOL.md`](extensions/spec/PROTOCOL.md) |
| Store | [`fampep/anikitty-extensions`](https://github.com/fampep/anikitty-extensions) · [`store/index.json`](https://github.com/fampep/anikitty-extensions/blob/main/store/index.json) |
| Disclaimer | [`extensions/DISCLAIMER.md`](extensions/DISCLAIMER.md) |

A link in the store is not fampep running that server. Community extensions stay with their authors.

## Build

```bash
cd apps/android
./gradlew :app:assembleRelease
```

The release APK is `apps/android/app/build/outputs/apk/release/`. Pull requests run unit tests, a debug APK, and lint. Pushes to `main` run the tests again and publish the release APK.

## License

Copyright 2026 fampep · [Apache License 2.0](LICENSE)

This product uses the TMDB API but is not endorsed or certified by TMDB.

Questions and bugs → [GitHub issues](https://github.com/fampep/AniFuse-android/issues). Chat → [Telegram](https://t.me/Anikittymoe). Support → [Ko-fi](https://ko-fi.com/E6C820K9H8). Pull requests → [CONTRIBUTING.md](CONTRIBUTING.md).
