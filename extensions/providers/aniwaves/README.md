# aniwaves

Bundled resolver for [aniwaves.tv](https://aniwaves.tv/). AniList stays the catalog. This resolver only matches a title.

Watch pages look like:

`https://aniwaves.tv/watch/kusuriya-no-hitorigoto-3rd-season-82762/ep-1`

Search uses `GET /filter?keyword=`. A card matches when its title is the show. A longer title that still contains that name is used only when the exact title is missing, and the shortest such title wins.

The episode asks `GET /ajax/server/list?servers={showId}&eps={n}` for the server list HTML, then `GET /ajax/sources?id={linkId}&asi=0&autoPlay=0` for each embed. Every named mirror in the preferred audio block (Vidplay, MegaPlay, …) opens in parallel — up to three at once — and every live playlist is returned so the player can failover without another resolve. Opposite-audio links are only used when that block is empty. Embeds resolve through `embed-N/getSources` or MegaPlay `stream/getSourcesNew` (AES when the payload is encrypted).

Playback sends `Referer` and `Origin` for the embed host, plus a browser `User-Agent`. The quality label is the tallest `RESOLUTION` on the master.

The extension version of aniwaves is built here. The phone still plays the stream itself.
