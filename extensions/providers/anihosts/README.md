# AniHosts

Bundled resolver. The phone asks anihosts.xyz for the AniList id and plays the episode master that site returns. AniList stays the catalog, and the AniList id is the watch id, so `/watch/attack-on-titan-16498/1` is Attack on Titan episode 1.

`GET /p/home/embed/anilist/{id}/{episode}` answers with a small `state-data` JSON: the episode list, the requested episode, and the host embed the site picked (`src`). A missing episode and an unknown title are that JSON and are never played.

The host embed (uqload for the current lineup) carries a packed jwplayer setup. The packed blob is unwrapped on device — the same token table the page's own script applies — and the first `sources[].file` is the HLS master. The phone opens that master with the embed page as `Referer`, the embed origin as `Origin`, and a browser `User-Agent`.

The master lists the original audio and the dub, so one URL serves a sub or a dub request; the app picks the track for the language the Sub / Dub control asked for. Sidecar captions are the VTT tracks the embed lists.

The extension version of AniHosts is built here. The phone still plays the stream itself.
