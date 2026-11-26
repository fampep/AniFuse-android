# AniFuse Extensions

**Public community home** for AniFuse stream-extension specs, guides, examples, and a curated **link** index.

> Educational custom-client project by **fampep**.  
> Third-party extensions are **community-run** — gladly contributed, not operated or controlled by the app owner.  
> Read [DISCLAIMER.md](./DISCLAIMER.md) before installing or publishing anything.

## Public GitHub

| Repo | Role |
|---|---|
| [`fampep/AniFuse-android`](https://github.com/fampep/AniFuse-android) | Android app. Compiles [providers](./providers) and plays streams on the phone. |
| [`fampep/anikitty-extensions`](https://github.com/fampep/anikitty-extensions) | This tree: spec, store, examples, and provider source. Owned by fampep. |

The in-app store loads **one** index:
`https://raw.githubusercontent.com/fampep/anikitty-extensions/main/store/index.json`  
(the current `main` branch). A refresh skips the raw-file cache. This monorepo
keeps **no store listings** — [`store/`](./store) only links to the
extensions repository.
Maintainer notes: [public GitHub setup](https://github.com/fampep/AniFuse-android/blob/main/docs/public-github-setup.md).

## License & ownership

- **Copyright 2026 fampep.** [Apache License 2.0](./LICENSE)
- Fork, modify, and contribute. See [DISCLAIMER.md](./DISCLAIMER.md) · [NOTICE](./NOTICE)
- Community extension *backends* stay owned by their authors — gladly linked, not controlled by fampep

## Quick links

| Doc | Purpose |
|---|---|
| [GUIDE.md](./GUIDE.md) | Author an extension |
| [spec/PROTOCOL.md](./spec/PROTOCOL.md) | HTTP resolve protocol 1.2 (`apiVersion` stays 1) |
| [spec/anikitty-extension.schema.json](./spec/anikitty-extension.schema.json) | Manifest schema |
| [store](./store) | Links to the store in the extensions repository |
| [examples/hello-http](./examples/hello-http) | Minimal template |
| [providers](./providers) | First-party resolvers the app compiles. They ship inside the APK |
| [CONTRIBUTING.md](./CONTRIBUTING.md) | How to contribute listings & docs |

## Design

- Phone plays video (Media3). Extensions only return playable URLs + headers.
- Sign-in is AniList on the phone. Extensions never receive account passwords.
- Users may paste any valid GitHub manifest URL — that path is fully user-directed.
