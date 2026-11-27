# AniRex

Bundled resolver for [anirex.to](https://anirex.to/).

Playback asks `/api/watch/{anilistId}/{episode}` for FlixCloud embeds (the same
path as `/watch/...-{anilistId}/{episode}`), then unwraps the playlist on the
phone. Dual-audio embeds pick sub or dub from the master playlist.
