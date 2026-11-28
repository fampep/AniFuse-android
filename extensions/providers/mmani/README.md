# MMani (AniBD)

On-device resolver for [mmani.com](https://mmani.com/) / AniBD mirrors.

## Flow

1. Boot `X-KF-Req` from the site HTML fetch wrapper.
2. `GET /ajax.php?episodes&epid={anilistId}` — AniList-keyed episode list.
3. `GET /ajax.php?eplink&data={link}` — embed URLs (play.anien.top).
4. Parse `window.PLAYER_CONFIG.videoUrl` → HLS master. Segments are `.jpg` paths with plain MPEG-TS bodies (no XOR / strip).

Sub-only. English VTT from the embed is attached when present.

Copyright 2026 fampep. Apache License 2.0.
