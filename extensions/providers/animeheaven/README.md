# AnimeHeaven

Bundled resolver for [animeheaven.me](https://animeheaven.me/).

Search is by title. The series page names the episode key. Playback opens
[`gate.php`](https://animeheaven.me/gate.php) with that cookie and plays the
direct mp4 on the phone. The first gate `<source>` is often an `ax.` promo
clip (“watch on site / Elite”); the phone skips it and plays the episode CDN
that carries the key. Sub only — the site does not list dubbed files.
