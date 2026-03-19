<div align="center">

<img src="./.github/assets/logo.png" alt="Ephyra logo" title="Ephyra logo" width="80"/>

# Ephyra

### Your content, your library, your way.

A personal media reader and library manager for Android — organize, track, and read your manga, comics, and webtoons in one place.

[![GitHub downloads](https://img.shields.io/github/downloads/Gameaday/Ephyra/total?label=downloads&labelColor=27303D&color=0D1117&logo=github&logoColor=FFFFFF&style=flat)](https://github.com/Gameaday/Ephyra/releases)
[![CI](https://img.shields.io/github/actions/workflow/status/Gameaday/Ephyra/build_push.yml?labelColor=27303D)](https://github.com/Gameaday/Ephyra/actions/workflows/build_push.yml)
[![License: Apache-2.0](https://img.shields.io/github/license/Gameaday/Ephyra?labelColor=27303D&color=0877d2)](/LICENSE)

## Download

[![Ephyra](https://img.shields.io/github/release/Gameaday/Ephyra.svg?maxAge=3600&label=Ephyra&labelColor=2c2c47&color=1c1c39&include_prereleases)](https://github.com/Gameaday/Ephyra/releases)

*Requires Android 8.0 or higher.*

</div>

## About

Ephyra is built for people who own their content. Whether your collection lives on a Jellyfin server, on your phone's local storage, or elsewhere, Ephyra helps you organize, track, and read it all in one place with a polished, configurable reading experience.

## Key Features

### 📖 Powerful Reading Experience
- **Multiple reading modes** — right-to-left, left-to-right, vertical scroll, and continuous webtoon modes with configurable tap zones and navigation.
- **Smooth page transitions** — choose between instant jump or smooth scroll when navigating with the page slider.
- **Smart page combine** — automatically merges short watermark or credit stubs with the preceding page for an uninterrupted reading flow.
- **Device-adaptive performance** — preload windows and download workers scale based on available RAM, keeping low-end devices responsive and high-end devices aggressive.
- **Bandwidth-isolated preloading** — adjacent chapters download in the background without competing with your active chapter, with opportunistic cross-chapter prefetch and automatic retry on failures.

### 🗄️ Jellyfin Integration
- Track read progress on your self-hosted Jellyfin media server.
- Bidirectional library sync — manage your library once, read everywhere.
- Automatic Jellyfin library scanning after sync (admin-gated).

### 🏷️ Authority-Based Metadata
- Pair series with authoritative metadata sources ([MangaUpdates](https://mangaupdates.com), [AniList](https://anilist.co/), [MyAnimeList](https://myanimelist.net/), Jellyfin) for rich, accurate library organization.
- **Per-field metadata priority** — choose whether each metadata field (title, description, cover, author, etc.) should prefer your content source or the authority source.
- **Field-level locking** — protect your manual edits from being overwritten during authority refresh.
- Smart content source matching with intelligent deduplication, prioritizing sources with available chapters.

### 📊 Tracking & Organization
- Tracker support: [MyAnimeList](https://myanimelist.net/), [AniList](https://anilist.co/), [Kitsu](https://kitsu.app/), [MangaUpdates](https://mangaupdates.com), [Shikimori](https://shikimori.one), [Bangumi](https://bgm.tv/), and [Jellyfin](https://jellyfin.org/).
- Categories to organize your library.
- Configurable authority source order — control which metadata source takes priority.
- Schedule automatic library updates for new chapters.

### 🎨 Customization
- Light and dark themes with branded theme options.
- E-Ink display mode with configurable flash intervals and colors.
- Color filters, grayscale, and inverted color modes for comfortable reading.
- Create backups locally or to your preferred cloud service.

## Contributing

[Code of conduct](./CODE_OF_CONDUCT.md) · [Contributing guide](./CONTRIBUTING.md)

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

Before reporting a new issue, take a look at the already opened [issues](https://github.com/Gameaday/Ephyra/issues).

### Credits

Thank you to all the people who have contributed!

<a href="https://github.com/Gameaday/Ephyra/graphs/contributors">
    <img src="https://contrib.rocks/image?repo=Gameaday/Ephyra" alt="Ephyra contributors" title="Ephyra contributors" width="800"/>
</a>

Ephyra builds on upstream open source reader technology and the work of many contributors.

### Disclaimer

The developer(s) of this application do not have any affiliation with the content providers available, and this application hosts zero content. Ephyra is intended for use with content you personally own or have authorized access to.

### License

<pre>
Copyright © 2015 Javier Tomás
Copyright © 2024 Ephyra Open Source Contributors

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
