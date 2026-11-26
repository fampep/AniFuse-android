# ani.pm

Bundled resolver. The phone searches **anipm.pk** (the live ani.pm host) and
plays that episode from its settlar server. The playlist is the HLS master on
`media.settlar.io`.

Search is `GET https://anipm.pk/api/anime/search`. Playback is the full
`GET /api/anime/playback-bootstrap/{source}/{id}` call, then
`/api/anime/settlar/session?provider=anipm`, then
`https://embed.settlar.io/api/embed/session`. The provider label in the app is
**ani.pm** — it is not renamed to another site.
