<div align="center">

<img src="./.github/assets/logo.png" alt="Mihon Nightly logo" title="Mihon Nightly logo" width="80"/>

# Mihon Nightly

### Full-featured reader — hardened nightly builds
Discover and read manga, webtoons, comics, and more – easier than ever on your Android device.

> **⚠️ This is an unofficial fork. It is not affiliated with, endorsed by, or connected to the [mihonapp/mihon](https://github.com/mihonapp/mihon) project or its team.**

[![GitHub downloads](https://img.shields.io/github/downloads/Gameaday/mihon/total?label=downloads&labelColor=27303D&color=0D1117&logo=github&logoColor=FFFFFF&style=flat)](https://github.com/Gameaday/mihon/releases)

[![CI](https://img.shields.io/github/actions/workflow/status/Gameaday/mihon/build_push.yml?labelColor=27303D)](https://github.com/Gameaday/mihon/actions/workflows/build_push.yml)
[![License: Apache-2.0](https://img.shields.io/github/license/Gameaday/mihon?labelColor=27303D&color=0877d2)](/LICENSE)

## Download

[![Mihon Nightly](https://img.shields.io/github/release/Gameaday/mihon.svg?maxAge=3600&label=Nightly&labelColor=2c2c47&color=1c1c39&include_prereleases)](https://github.com/Gameaday/mihon/releases/tag/nightly)

*Requires Android 8.0 or higher.*

## About this fork

Mihon Nightly is a hardened, up-to-date build of [Mihon](https://github.com/mihonapp/mihon) that tracks the upstream project closely. It prioritises keeping the latest improvements available, at a trade-off of possible reduced stability compared to official releases.

**This fork:**
- Is not affiliated with the upstream Mihon team or project.
- Does not include upstream donation links.
- Routes its in-app updater to [Gameaday/mihon](https://github.com/Gameaday/mihon) releases.

## Improvements over upstream

The following changes have been made on top of the base Mihon project:

- **Device-adaptive reader performance.** Preload window sizes and download worker counts are scaled automatically based on available device RAM, so low-end devices stay conservative while high-end devices prefetch more aggressively.
- **Bandwidth-isolated chapter preloading.** Adjacent chapters are fetched in the background using a single throttled worker, preventing speculative preloads from competing with the active chapter's downloads. Worker concurrency scales up only when that chapter becomes the one being read.
- **Opportunistic cross-chapter image prefetch.** When the current chapter's download buffer has headroom, the first pages of the next chapter begin downloading before the user reaches the chapter boundary, reducing wait time at transitions.
- **Per-page preload re-trigger.** If a cross-chapter prefetch was deferred due to a thin buffer, it is retried on every page advance rather than waiting for the next full preload cycle, so downloads start the moment bandwidth is available.
- **Backward page preloading.** Pages behind the current reading position are also queued for download at low priority, reducing stutter when navigating backward through a chapter.
- **Automatic retry with exponential backoff.** Transient page-load failures (network I/O errors, HTTP 429 and server errors) are retried up to three times with increasing delays before the page is marked as failed.

## Features

<div align="left">

* Local reading of content.
* A configurable reader with multiple viewers, reading directions and other settings.
* Tracker support: [MyAnimeList](https://myanimelist.net/), [AniList](https://anilist.co/), [Kitsu](https://kitsu.app/), [MangaUpdates](https://mangaupdates.com), [Shikimori](https://shikimori.one), and [Bangumi](https://bgm.tv/) support.
* Categories to organize your library.
* Light and dark themes.
* Schedule updating your library for new chapters.
* Create backups locally to read offline or to your desired cloud service.
* Plus much more...

</div>

## Contributing

[Code of conduct](./CODE_OF_CONDUCT.md) · [Contributing guide](./CONTRIBUTING.md)

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

Before reporting a new issue, take a look at the already opened [issues](https://github.com/Gameaday/mihon/issues).

### Credits

Thank you to all the people who have contributed to the upstream Mihon project and this fork!

<a href="https://github.com/mihonapp/mihon/graphs/contributors">
    <img src="https://contrib.rocks/image?repo=mihonapp/mihon" alt="Mihon app contributors" title="Mihon app contributors" width="800"/>
</a>

### Disclaimer

This is an unofficial fork of Mihon and is **not affiliated with the Mihon Open Source Project**. The developer(s) of this application do not have any affiliation with the content providers available, and this application hosts zero content.

### License

<pre>
Copyright © 2015 Javier Tomás
Copyright © 2024 Mihon Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
</pre>

</div>
