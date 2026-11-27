# Extension store

The store index lives in the extensions repository — this tree keeps no
listings:

**[fampep/anikitty-extensions → `store/`](https://github.com/fampep/anikitty-extensions/tree/main/store)**

The AniFuse app loads it directly from
`https://raw.githubusercontent.com/fampep/anikitty-extensions/main/store/index.json`.

Sites the phone already knows — [AniRex](https://anirex.to/), [AnimeHeaven](https://animeheaven.me/), [0neko](https://beta.0neko.cc/) — stay on the list even when that index has not published them yet. Missing episodes stay soft; only unreachable sources cool out of the race. A site with no resolver still opens in the browser and stays out of the race.

Submit or update a listing by opening an issue or PR in that repository —
see its [`store/SUBMIT.md`](https://github.com/fampep/anikitty-extensions/blob/main/store/SUBMIT.md).
