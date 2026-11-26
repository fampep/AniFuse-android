# AniFuse Android

The phone app. Catalog and sign-in are AniList. Media3 plays on the device. Streams resolve through the **AniFuse resolver API** — the hosted demo ships by default, and Settings → AniFuse API can point the app at your own instance (self-host or offline, no restart needed). Community extensions install from the in-app Store or a manifest URL. Watch progress stays on the phone and can sync to AniList.

For install links, the stable and dev builds, and the license, see the [root README](../../README.md).

1. Sign in with AniList. The redirect URL is `anikitty://anilist`.
2. Browse Home, Discover, and Search against the AniList catalog.
3. Continue from progress stored on the device. Completed shows stay in the Library.
4. Play an episode — the resolver API asks every healthy provider in parallel and returns ranked streams (HLS/MP4, captions, required headers). Sources can be switched inside the player.
5. Provider health, success rates, and cooldowns are live under Settings → AniFuse API → Provider health.

Debug APKs allow cleartext HTTP globally. Release builds block cleartext.

## Build APK

```bash
./gradlew :app:assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

Version name and version code come from the repo-root `VERSION` file.
