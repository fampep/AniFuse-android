# AniGato

Bundled resolver. The phone asks anigato.lol for the AniList id and plays the episode playlist that site returns. AniList stays the catalog. The watch id is that AniList id, so `/watch/116674/1` is Bleach: Thousand-Year Blood War episode 1, and Naruto stays id 20.

`GET /api/episodes/{id}` lists episode numbers. A number missing from that list is not played. `GET /api/watch/{id}/sub|dub/{episode}` names the files. A dub request plays only rows marked dub. A sub request plays only rows marked sub.

The playable file is an `https://anigato.lol/api/stream/` master. The phone keeps it when the body is an HLS playlist, and it asks for that file with `Referer`, `Origin`, and `User-Agent`. The self-hosted `master.m3u8` links on the same response are not playlists, so they are left alone. Quality is the `RESOLUTION` on the master when the site states one.

Sidecar captions are `/api/sub.vtt` on anigato.lol. Intro and outro still come from AniSkip when this response does not include them.

The extension version of AniGato is built here. The phone still plays the stream itself.
