# ani.pm

Bundled resolver. **One** Store / Race card (`anipm`) — `ani.nz` / `ani.vg`
are only mirror domains on the brand Player API, not a second site.

The phone uses ani.pm’s first-party Player API (same shape as the public
embed docs):

1. Resolve MAL from `mal:` on the title hint, `/api/v1/lookup?anilist=`, or
   `/api/v1/titles?q=`
2. `POST /api/session` with `{ malId, episode }` → short-lived token
   (session-first; embed HTML only when the POST needs host cookies)
3. Play `/playlists/{mal}_ep{n}/{sub|dub}.direct.pls?t=…` on this phone
4. [AnipmXorDataSource] honors `#EXT-X-BYTERANGE` (stream-XOR on read) and
   falls back to full PNG + IEND cut so Media3 sees clear MPEG-TS

Live origins:

**Brand** (parallel race, best hit wins — height / subs / RTT):

- `https://anipm.pk`
- `https://ani.pm`
- `https://ani.nz`
- `https://ani.vg`

**Fallback** (different product — only when every brand edge CF-challenges
OkHttp):

- `https://animepapa.cyou` — AnimePapa Player API (also used by HinataTV)

Challenged brand hosts are demoted for 10 minutes so Play is not held by
serial Cloudflare challenge pages. The old `embed.settlar.io` session path
and the standalone `aninz` Race card are not used.

Copyright 2026 fampep. [Apache License 2.0](../LICENSE).
