# ani.pm

Bundled resolver. One Store card (`anipm`) — no second ani.pm / settlar mirror.

The phone uses ani.pm’s first-party Player API (same shape as the public
embed docs):

1. Resolve MAL from `mal:` on the title hint, `/api/v1/lookup?anilist=`, or
   `/api/v1/titles?q=`
2. Warm `/embed/mal/{mal}/{ep}/{sub|dub}` for same-origin cookies
3. `POST /api/session` with `{ malId, episode }` → short-lived token
4. Play `/playlists/{mal}_ep{n}/{sub|dub}.direct.pls?t=…` on this phone

Live origins tried in order: `https://anipm.pk`, then `https://ani.pm`.
The old `embed.settlar.io` session path is not used.

Copyright 2026 fampep. [Apache License 2.0](../LICENSE).
