# ani.pm

Bundled resolver. **One** Store / Race card (`anipm`) — `ani.nz` is only a
mirror domain on the same Player API, not a second site.

The phone uses ani.pm’s first-party Player API (same shape as the public
embed docs):

1. Resolve MAL from `mal:` on the title hint, `/api/v1/lookup?anilist=`, or
   `/api/v1/titles?q=`
2. Warm `/embed/mal/{mal}/{ep}/{sub|dub}` for same-origin cookies
3. `POST /api/session` with `{ malId, episode }` → short-lived token
4. Play `/playlists/{mal}_ep{n}/{sub|dub}.direct.pls?t=…` on this phone
5. [AnipmXorDataSource] honors `#EXT-X-BYTERANGE` (stream-XOR on read) and
   falls back to full PNG + IEND cut so Media3 sees clear MPEG-TS

Live origins tried in order (warm last-good first when cached):

- `https://animepapa.cyou` — Player API apex (OkHttp-reachable)
- `https://anipm.pk`
- `https://ani.pm`
- `https://ani.nz`

The old `embed.settlar.io` session path and the standalone `aninz` Race card
are not used.

Copyright 2026 fampep. [Apache License 2.0](../LICENSE).
