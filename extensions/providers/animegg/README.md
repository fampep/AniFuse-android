# AnimeGG

Bundled resolver. The phone searches animegg.org and plays the episode file that site embeds. AniList stays the catalog. This resolver only matches a title.

Search is `GET https://www.animegg.org/search/?q=`. A card matches when its title, or an alternate title on that same card, is the show. A movie or later season with a different title is left alone.

The series page lists every episode under `ul.newmanga`. The episode page is `/{slug}-episode-{number}`. Sub, dub, and raw are tabs on that page. A sub request plays the subbed file, or the raw file when the page has no subbed tab. A dub request plays only the dubbed tab.

The tab iframe is `/embed/{id}` on animegg.org. That public player lists `videoSources` as `/play/{id}/video.mp4` with a label such as `1080p`. The phone requests that file with `Referer`, `Origin`, and `User-Agent`. The site redirects it to the mp4. Backup embed hosts named beside the file are not used.

The quality label is the file label when it states a size. A grade word is kept only when that same label states one. Sub or dub follows the tab.

The extension version of AnimeGG is built here. The phone still plays the stream itself.
