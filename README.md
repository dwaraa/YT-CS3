# YouTube CloudStream 3 Provider

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-purple.svg)](https://kotlinlang.org)
[![CloudStream 3](https://img.shields.io/badge/CloudStream-3-orange.svg)](https://cloudstream.cf/)

A high-performance **CloudStream 3** provider plugin for **YouTube**, powered by **NewPipeExtractor** and CloudStream's native streaming engine.

> [!WARNING]
> ### ⚠️ Unofficial Disclaimer
> This project is an **unofficial**, open-source extension and is **NOT affiliated with, authorized, maintained, sponsored, or endorsed by YouTube, Google LLC, or any of their affiliates or subsidiaries**.
> All YouTube logos, trademarks, and brand names are the property of their respective owners. This plugin does not bypass paywalls, host media files, or modify proprietary YouTube services; it strictly parses public content using the open-source [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) library.

---

## 🚀 Features

### 🎛️ Custom Home Screen Feeds & Subscriptions
- **Subscribed Channels Feed**:
  - **Combined Latest Feed (`Subscribed: Latest Videos`)**: Gathers recent uploads across all your subscribed channels into a single unified row, interleaved chronologically.
  - **Dedicated Channel Rows (`Channel: <Name>`)**: Each subscribed channel can have its own dedicated horizontal row on your home screen.
- **Interactive In-App Settings UI**:
  - Access via **Settings > Extensions > YouTube (Gear Icon)**.
  - **➕ Add Subscribed Channel**: Enter any channel handle (e.g., `@MrBeast`, `@mkbhd`), channel URL, or channel ID.
  - **📥 Import Subscriptions**: Migrate subscriptions in seconds from **NewPipe JSON**, **Google Takeout CSV**, or **OPML XML** with one-tap clipboard paste.
  - **📤 Export Subscriptions**: Backup and transfer subscriptions as NewPipe JSON or Takeout CSV.
  - **👁️ Unwatched Only**: Toggle to hide watched videos from your subscription feed with an "All caught up" indicator.
  - **⚡ Shorts Handling**: Choose between dedicated Shorts row (`YouTube Shorts ⚡`), inline feeds, or hiding shorts entirely.
  - **🌍 Region & Language**: Select your country and preferred language for trending and popular feeds.
  - **🔢 Row Customization**: Reorder home rows and adjust per-row item limits (10 to 40 items).
  - **📋 Manage Subscriptions**: View and delete subscribed channels.
  - **🔄 Reset to Defaults**: Restore default configuration.

### 👥 Multi-Tab Channel Exploration (Stage 4)
- **Channel Tabs as Seasons**: When browsing any channel, all available tabs are organized into seasons:
  - **Season 1**: Videos (long-form uploads)
  - **Season 2**: Shorts (`⚡` vertical videos)
  - **Season 3**: Live Streams (`🔴` ongoing and past broadcasts)
  - **Season 4**: Playlists (`📋` channel playlists)

### 🔍 Search Filters & Real-Time Suggestions (Stage 4)
- **Search Filters**: Filter searches directly using intuitive syntax:
  - `type:video`, `type:channel`, `type:playlist`, `type:shorts`
  - `sort:date`, `sort:views`, `sort:rating`, `sort:relevance`
  - `duration:short` (<4m), `duration:medium` (4-20m), `duration:long` (>20m)
- **Search Suggestions**: Instant query completions powered by NewPipe's search suggestion engine.
- **Search History**: Recent search queries are saved locally with one-tap history clearing.

### 📖 Rich Video Details & Chapters (Stage 4)
- **Video Statistics**: Displays formatted view counts (`👁️ 1.2M views`), likes (`👍 85K likes`), and exact upload dates.
- **Chapter Markers**: Formatted chapter timestamps (`02:15 - Chapter Title`) rendered right in the video description.
- **Metadata**: Category tags, upload year, and channel subscriber count.

### 🎵 Video & Audio Playback Quality (Stage 2)
- **Adaptive 1080p / 1440p (2K) / 4K (2160p) with Audio**:
  - Automatically pairs high-resolution separate video-only streams (1080p60, 1440p, 4K) with the highest-bitrate matching audio stream via CloudStream's native ExoPlayer track merging.
  - Full audio and video synchronization for crisp 60fps and ultra-high-definition playback.
- **Audio Track Selection & Labeling**:
  - Full inspection of audio streams with language name, `[ORIGINAL]` vs `[DUBBED]` status, bitrate (e.g., `160kbps`), and codec (`M4A`, `Opus`).
  - Automatically generates additional dubbed video links when alternative language dubs (e.g. MrBeast international dubs) are available.
  - Standalone high-bitrate audio streams available for pure listening with display off / low bandwidth.
- **Numeric Quality Sorting & Preferred Quality Matching**:
  - Resolves and sorts all streams strictly by numeric pixel height (`2160`, `1440`, `1080`, `720`, `480`, `360`) rather than fragile string prefixes.
  - Correctly matches CloudStream preferred quality settings (e.g. `2160p (4K)`, `1080p (Full HD)`), badging matched streams with `⭐ [Preferred]` and placing them at the top of the link list.
- **Safe Fallback Links**:
  - Always includes pre-muxed 360p progressive fallback streams (`[Muxed Fallback]`) at the bottom of the list for weak network conditions or legacy hardware.
- **Live Streams**:
  - Active live broadcasts stream via adaptive HLS `.m3u8` master playlists.
  - Upcoming scheduled live streams display scheduled start times and reminders; ended streams cleanly notify the user that VOD processing is underway.
- **Subtitles & Closed Captions**:
  - Uploaded closed captions and auto-generated transcripts with explicit `(Auto-generated)` tags.
  - Subtitles are sorted and prioritized based on user's preferred subtitle language.
- **DASH Manifests**:
  - Adaptive DASH MPD stream pass-through with `ExtractorLinkType.DASH`.

### ⚡ Stage 5: Power Features (Standout Features)
- **⚡ SponsorBlock Integration**:
  - Automatically queries the SponsorBlock community database (`sponsor.ajay.app`).
  - Renders sponsor segment breakdown in the video details with timestamps (`Sponsor`, `Intro / Animation`, `Outro / Credits`, `Selfpromo`).
  - Injects a `⚡ SponsorBlock Markers` WebVTT subtitle track directly into CloudStream's player, marking sponsor skips on screen.
- **👎 Return YouTube Dislike (RYD)**:
  - Fetches real-time dislikes and ratings from the Return YouTube Dislike API.
  - Displays formatted like/dislike counts (`👍 12.5K | 👎 342 (97.3%)`) and sets the video's star rating.
- **📖 Seekable Chapters & Transcripts**:
  - Automatically extracts timestamped video chapters and renders a dedicated `📖 Seekable Chapters` WebVTT cue track for player seeking.
  - Formats full chapter list and lists available closed captions / language transcripts in the video description.
- **💾 Local Playlists & "Watch Later"**:
  - Offline local playlist management and dedicated "Watch Later" row on the home screen.
  - Create custom playlists (e.g. "Favorites", "Podcasts"), add/remove items, and play local playlists as native TV series with episodes.
- **💬 Top Comments & Related Recommendations**:
  - Extracts and formats top viewer comments directly in the video overview.
  - Populates CloudStream's native recommendations carousel with related videos parsed from the stream.
- **🎧 Audio-Only / Background Mode**:
  - Dedicated Audio-Only mode prioritizing pure M4A and Opus audio streams with highest quality scores.
  - Perfect for listening to music, lectures, and podcasts with display off or minimal data usage.
- **👥 Multiple Profiles (Kids, Music, Work, Default)**:
  - Create separate profiles, each with its own subscribed channels, home feed ordering, item limits, and watch history.
  - Quick-switch profiles with one tap in settings.
- **🩺 In-App Self-Test & Diagnostics**:
  - Built-in diagnostic suite that tests NewPipe version (`v0.26.5`), queries YouTube extraction, pings YouTube/RYD/SponsorBlock API latencies, checks consent cookie health, and verifies audio track merging.
  - Generates a comprehensive markdown report with a "Copy Report" button for instant sharing and troubleshooting.

### 🛡️ Reliability & Resilience
- **Error Transparency**: Clear in-app error feedback (e.g., bot checks, age restrictions, region blocks) instead of silent failures.
- **Negative Caching & Backoff**: Failing channels are placed on a short 60-second backoff to prevent hammering the network.
- **Refreshable Consent Cookies**: Automatically catches and refreshes YouTube consent tokens (`SOCS`, `CONSENT`) and persists them locally.
- **ExoPlayer Compatibility**: WebM (VP9) streams are prioritized over fragmented MP4 chunks to eliminate `ERROR_CODE_DECODER_INIT_FAILED (4001)`.

---

## ⚠️ Known Limitations & FAQ

### Q: How does 1080p and 4K playback work?
**A:** YouTube stores all resolutions above 720p as separate video-only and audio-only streams. This plugin uses CloudStream's ExoPlayer multi-source track merging (`audioTracks`) to pair the highest-fidelity video stream with the best audio track, providing seamless 1080p, 1440p, and 4K video with synchronized sound.

### Q: Why are there multiple audio and dubbed links?
**A:** YouTube channels with multi-language audio (such as MrBeast) provide separate audio tracks for different languages. The provider labels each audio stream as `[ORIGINAL]` or `[DUBBED]` with language, codec, and bitrate, and generates dedicated paired video links for secondary dubs so you can watch in your preferred language.

### Q: Why do age-restricted or members-only videos fail?
**A:** Age-restricted and paid/member-only videos require an authenticated Google account with age verification. This provider operates anonymously via NewPipeExtractor without Google Play Services or account sign-in. When encountered, the plugin cleanly displays a helpful message explaining why the video cannot be played.

### Q: Why do I see a "Bot Verification Required" error?
**A:** Google occasionally triggers bot verification / reCAPTCHA challenges when requests originate from VPNs, datacenter IPs, or heavily queried subnets. The provider automatically refreshes consent cookies, but if your IP is temporarily flagged by Google, disconnect your VPN or retry after a few minutes.

### Q: Why does loading a channel page take a few seconds?
**A:** The plugin parses live channel data directly without relying on proprietary API keys. To ensure subsequent interactions are instant, loaded channel videos and feeds are cached in memory for 5 minutes.

### Q: Does this plugin bypass YouTube ads?
**A:** Direct stream extraction via NewPipe naturally plays streams without YouTube client-side video ads. SponsorBlock integration for skipping sponsored segments is planned for Stage 3.

---

## 📁 Project Structure

```
YT-CS3/
├── .github/
│   ├── ISSUE_TEMPLATE/
│   │   ├── bug_report.md           # Bug report template with logcat & versions
│   │   └── feature_request.md      # Feature request template
│   ├── workflows/
│   │   └── build.yml               # Automated CI/CD (PR testing, ktlint, .cs3 build & publish)
│   └── dependabot.yml              # Automated dependency update PRs
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── YouTube/
│   ├── build.gradle.kts            # Cloudstream plugin metadata (version 5)
│   ├── icon.png                    # High-res YouTube provider icon
│   ├── src/main/java/com/youtube/
│   │   └── AudioHelper.java        # Cross-module Java bridge for AudioFile multi-source playback
│   ├── src/main/kotlin/com/youtube/
│   │   ├── YoutubePlugin.kt        # @CloudstreamPlugin entry point with openSettings hook
│   │   ├── YoutubeProvider.kt      # MainAPI implementation delegating to modular handlers
│   │   ├── YoutubePreferences.kt   # Typed settings model (HomeRowType, cache TTL, timeouts)
│   │   ├── YoutubeSettings.kt      # Top-level settings BottomSheet orchestrator
│   │   ├── YoutubeDownloader.kt    # Custom OkHttp Downloader with cookie refresh
│   │   ├── extractor/
│   │   │   ├── YoutubeLinkResolver.kt  # Adaptive 1080p/4K pairing, numeric matching, dubs
│   │   │   └── YoutubeEpisodeMapper.kt # Episode/search mapping, date sorting, de-duplication
│   │   ├── feed/
│   │   │   └── YoutubeFeedHandler.kt   # Parallel subscription fetching, timeouts, and cache
│   │   ├── loader/
│   │   │   └── YoutubeLoaders.kt       # Video, channel, and playlist loaders with pagination
│   │   ├── search/
│   │   │   └── YoutubeSearchHandler.kt # Multi-page catalog search and quick search
│   │   ├── subscription/
│   │   │   └── YoutubeSubscriptionImporter.kt # Importer/exporter (NewPipe JSON, CSV, OPML)
│   │   └── ui/
│   │       ├── SettingsTheme.kt        # Color tokens, drawables, and avatar bitmap cache
│   │       ├── SettingsComponents.kt   # Reusable UI cards, switches, rows, and picker modals
│   │       ├── SettingsContentSection.kt # Content and search settings cards
│   │       └── SubscriptionDialogs.kt  # Subscription import/export & channel management
│   └── src/test/kotlin/com/youtube/
│       ├── YoutubeUrlUtilsTest.kt      # URL and handle parsing, video ID sanitation tests
│       ├── YoutubeQualityTest.kt       # Numeric quality parsing and matching tests
│       ├── YoutubeDateParserTest.kt    # Relative upload date parsing and sorting tests
│       ├── YoutubePreferencesTest.kt   # Typed settings and SubscribedChannel JSON tests
│       ├── YoutubeSearchHandlerTest.kt # Search filter query parsing tests
│       └── YoutubeSubscriptionImporterTest.kt # Subscription import & export tests
├── build.gradle.kts                # Root Gradle configuration with testOptions & coroutines
├── settings.gradle.kts             # Gradle multi-project settings
├── gradle.properties               # JVM and compiler tuning
├── repo.json                       # Cloudstream repository manifest
├── CHANGELOG.md                    # Changelog adhering to Keep a Changelog
├── CONTRIBUTING.md                 # Contribution guidelines, build steps & NewPipe checklist
├── LICENSE                         # GNU General Public License v3.0
└── README.md                       # Project documentation
```

---

## 🛠️ Building Locally

To build the plugin `.cs3` artifact locally:

```bash
./gradlew make
```

To generate `plugins.json`:

```bash
./gradlew makePluginsJson
```

Compiled `.cs3` bundles are generated in:
```
YouTube/build/YouTube.cs3
```

For comprehensive build prerequisites, developer instructions, debugging tips, and the **NewPipeExtractor update checklist**, see [CONTRIBUTING.md](CONTRIBUTING.md).

---

## 📲 Installing in CloudStream 3

1. Open **CloudStream 3**.
2. Navigate to **Settings** > **Extensions**.
3. Tap **Add Repository**.
4. Enter the repository details:
   - **Repository Name**: `YouTube Provider`
   - **Repository URL**:
     ```
     https://raw.githubusercontent.com/dwaraa/YT-CS3/builds/repo.json
     ```
5. Tap **Download/Setup**.
6. Find **YouTube** and tap **Install**.
7. Tap the **Gear (⚙️) icon** next to YouTube to customize your subscribed channels and home screen rows!

---

## 📜 License & Acknowledgments

This project is licensed under the [GNU General Public License v3.0](LICENSE).

- Powered by [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0).
- Built for [CloudStream 3](https://github.com/recloudstream/cloudstream) (GPL-3.0).
