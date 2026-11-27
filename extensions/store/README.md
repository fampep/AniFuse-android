# Extension store

The store index lives in the extensions repository — this tree keeps no
listings:

**[fampep/anikitty-extensions → `store/`](https://github.com/fampep/anikitty-extensions/tree/main/store)**

The AniFuse app loads it directly from
`https://raw.githubusercontent.com/fampep/anikitty-extensions/main/store/index.json`.

Sites the phone already knows, including [AniRex](https://anirex.to/) and [AnimeHeaven](https://animeheaven.me/), stay on the list even when that index has not published them yet. AniRex plays FlixCloud embeds from the AniList watch API. AnimeHeaven plays the gate.php mp4. A site with no resolver still opens in the browser and stays out of the race.

Submit or update a listing by opening an issue or PR in that repository —
see its [`store/SUBMIT.md`](https://github.com/fampep/anikitty-extensions/blob/main/store/SUBMIT.md).
