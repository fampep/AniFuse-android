# AniVault

On-device resolver for [anivault.tv](https://anivault.tv/).

The phone calls the same public source buttons the site uses (`/api/anikoto_stream.php`, `/api/reanime_stream.php`, and the rest) on `https://www.anivault.co`. A show is found by the MAL id on the title hint, or by an exact title on `/api/hoshika/search`. The winning file plays here. The private scraper key stays on their server.
