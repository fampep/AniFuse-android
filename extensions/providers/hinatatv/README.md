# HinataTV

Bundled resolver for [hinatatv.site](https://hinatatv.site/).

Watch URLs are AniList ids (`/watch/195539`). Availability comes from
`/api/watch/availability`. Playback uses the AnimePapa embed HinataTV
iframes: `/embed/ani/{anilist}/{ep}/{sub|dub}` redirects to a MAL embed,
then a short-lived session unlocks the PNG byte-range HLS playlist that
Media3 already plays for ani.pm. Sub and dub when both lanes exist.
