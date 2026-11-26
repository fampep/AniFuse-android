# aniwaves

Bundled resolver. The phone searches aniwaves.tv and plays the episode that site embeds. AniList stays the catalog. This resolver only matches a title.

Search is `GET https://aniwaves.tv/filter?keyword=`. A card matches when its title is the show. A longer title that still contains that name is used only when the exact title is missing, and the shortest such title wins.

The episode is `/ajax/server/list` for the show id, then the embed `getSources` playlist. Sub or dub follows the server block on that list. Subtitle tracks and intro or credits are kept when the source response states them.

Playback sends `Referer` and `Origin` for the embed host, plus a browser `User-Agent`. The quality label is the tallest `RESOLUTION` on the master.

The extension version of aniwaves is built here. The phone still plays the stream itself.
