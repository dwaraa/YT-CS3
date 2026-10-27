# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Planned
- SponsorBlock integration for skipping sponsor segments.
- Return YouTube Dislike (RYD) API integration.

---

## [1.4.0] - 2026-10-27

### Added
- **Subscription Migration (Import & Export)**:
  - Full support for importing subscriptions from **NewPipe JSON** (standard backup format or raw array), **Google Takeout CSV** (`Channel Id,Channel Url,Channel Title`), and **OPML XML** (RSS subscriptions outline).
  - Automatic format detection with one-tap clipboard paste integration and duplicate channel prevention.
  - Subscriptions export dialog with options for NewPipe JSON (full backup) and Google Takeout CSV with direct clipboard copying.
- **Watch Progress & "Unwatched Only" Filter**:
  - Leverages CloudStream's native Room database resume watching status (`DataStoreHelper.getViewResume`) to detect completed videos.
  - Added "Unwatched Only" toggle in Settings to filter out watched videos from subscription feeds, with an "All caught up!" indicator when complete.
  - Independent watched tracking set with option to clear watch history.
- **Shorts Handling Options**:
  - Three customizable modes: **Show in feeds** (inline), **Dedicated Shorts row** (`YouTube Shorts ⚡` home row), and **Hide all Shorts**.
  - Dynamic shorts detection via `isShortFormContent()`, `#shorts` tag, vertical aspect, and URL patterns.
- **Multi-Tab Channel Browsing**:
  - Channels now expose distinct tabs mapped directly to CloudStream seasons:
    - **Season 1**: Videos
    - **Season 2**: Shorts (`⚡`)
    - **Season 3**: Live Streams (`🔴`)
    - **Season 4**: Playlists (`📋`)
  - Enables full channel catalog exploration directly from CloudStream's season selector dropdown.
- **Search Filters, Suggestions & History**:
  - Structured query filter parsing: inline filter syntax for `type:video`, `type:channel`, `type:playlist`, `type:shorts`, `sort:date`, `sort:views`, `sort:rating`, and `duration:short|medium|long`.
  - Default search content filter and sort order preferences in Settings.
  - Real-time search suggestions via NewPipe's `suggestionList` and persistent 20-item search query history.
- **Richer Video Details**:
  - Streamlined presentation of view counts (`1.4M views`), likes (`45K likes`), and textual upload dates formatted neatly in video headers.
  - Chapter markers parsed from `streamSegments` and rendered with formatted timestamps (`02:15 - Chapter Title`) in descriptions.
  - Genre tags enriched with video category and topic keywords.
- **Feed Customization & Personalization**:
  - Configurable per-row item limits (10, 15, 20, 25, 30, 40 videos per row).
  - Home feed row reordering supporting all feed types.
  - Region selection for Trending feeds (US, GB, IN, CA, DE, FR, JP, BR, AU, KR, MX, IT).
  - Content language localization (en, es, hi, fr, de, ja, ko, pt, ar).
- **Unit Testing**:
  - Added unit test suites for `YoutubeSubscriptionImporterTest` (5 tests) and `YoutubeSearchHandlerTest` (3 tests), bringing total passing unit tests to 21 across 6 test suites.

---

## [1.3.0] - 2026-10-27

### Added
- **Speed & Concurrency**:
  - Parallel channel subscription fetching with a concurrency cap of 5 using Kotlin coroutines and `Semaphore`.
  - Added per-row timeouts (`withTimeoutOrNull`, default 6s) preventing any slow or failing channel from blocking home screen generation.
  - Configurable in-memory feed cache TTL (1m, 5m, 15m, 30m, 60m) and an instant "Refresh Now" action in Settings.
  - Strictly sorted the combined subscriptions feed chronologically by upload date using parsed exact/relative timestamps instead of simple round-robin interleaving.
  - Automatic de-duplication and capping of combined feed results (default 50 items) for fast rendering.
- **Modular Code Architecture**:
  - Decomposed 1,158-line `YoutubeProvider.kt` into focused modules:
    - `YoutubeFeedHandler`: handles parallel feeds, caching, and timeouts.
    - `YoutubeSearchHandler`: handles catalog and quick search.
    - `YoutubeLoaders`: handles video, channel, and playlist extraction.
    - `YoutubeLinkResolver`: handles stream extraction, adaptive pairing, and subtitle sorting.
    - `YoutubeEpisodeMapper`: unified episode/search response mapping and upload date parsing.
  - Decomposed 1,287-line `YoutubeSettings.kt` into reusable UI modules:
    - `SettingsTheme`: color tokens, drawable builders, and avatar bitmap cache.
    - `SettingsComponents`: card groups, switch rows, setting rows, and selection modals.
    - `SubscriptionDialogs`: channel addition and subscription management.
  - Replaced scattered boolean preference properties with a typed settings model (`HomeRowType`, `isRowEnabled`, `setRowEnabled`).
  - Unified episode building and raised channel and playlist page limits from 5-6 pages up to 15 pages (~450 videos).
- **Quality Gates & CI**:
  - Comprehensive unit test suite with 12 unit tests across `YoutubeUrlUtilsTest`, `YoutubeQualityTest`, `YoutubeDateParserTest`, and `YoutubePreferencesTest`.
  - Updated CI workflow (`.github/workflows/build.yml`) to run on pull requests, execute unit tests, run `ktlint`, and cache Gradle dependencies.

---

## [1.2.0] - 2026-10-27

### Added
- **Adaptive 1080p, 1440p (2K), and 4K (2160p) Playback**:
  - Automatically pairs high-resolution separate video-only streams (including 1080p60 and 4K) with the highest-bitrate matching audio stream via CloudStream's `AudioFile` and ExoPlayer's `MergingMediaSource`.
  - Java bridge `AudioHelper.java` to instantiate CloudStream's `AudioFile` across Kotlin module boundaries.
- **Audio Track Selection & Labeling**:
  - Detailed inspection of audio streams: language display name, `[ORIGINAL]` vs `[DUBBED]` status, bitrate (e.g. `160kbps`), and codec (`M4A`, `Opus`).
  - Added dedicated video links for dubbed tracks when multi-language dubs are available.
  - Added standalone audio stream links for display-off or low-bandwidth listening.
- **Numeric Quality Sorting & Preferred Matching**:
  - Parse and sort streams strictly by numeric pixel height (`2160`, `1440`, `1080`, `720`, `480`, `360`).
  - Correct matching for complex preferred quality labels (e.g. `2160p (4K)`), badging matched links with `⭐ [Preferred]` and placing them at the top.
- **Safe Fallback & Link Prioritization**:
  - Prioritizes preferred quality and highest resolution at the top of the link list.
  - Retains a guaranteed pre-muxed 360p progressive stream at the bottom as `[Muxed Fallback]` for legacy or low-bandwidth devices.
- **Live Stream Enhancements**:
  - Adaptive HLS `.m3u8` master playlist for active live streams.
  - Clear user-facing notices for scheduled/upcoming live streams (with start time) and ended live streams (awaiting VOD conversion).
- **Subtitle Improvements**:
  - Auto-generated captions explicitly tagged with `(Auto-generated)`.
  - Prioritizes subtitles according to user's preferred subtitle language.

---

## [1.1.0] - 2026-10-27

### Added
- **Error Detection & User-Facing Messaging**:
  - Implemented specific error recognition for YouTube hurdles: Bot Verification (reCAPTCHA), Age Restrictions, Region/Geographic blocks, Members/Paid content, and HTTP 429 Rate Limiting.
  - Replaced silent crashes with informative `ErrorLoadingException` banners and descriptive error cards.
- **Negative Caching & Backoff**:
  - Added a 60-second failure backoff (`failedChannelsCache`) to prevent repeatedly hammering unavailable channels or networks.
- **Meaningful Feed States**:
  - Empty or failing channels now render interactive retry cards with error reasons (e.g. `⚠️ Couldn't load <Channel> (<Reason>) - Tap to retry`) instead of invisible empty rows.
- **Dynamic Avatar Resolution**:
  - Removed truncated static avatar URLs. Channel avatars are now resolved dynamically at runtime and cached/persisted across sessions.
- **Refreshable Consent Cookies**:
  - Implemented automatic cookie capture and refresh (`refreshConsentCookies()`) in `YoutubeDownloader`.
  - Refreshed cookies (`SOCS`, `CONSENT`, `PREF`) are persisted locally via `SharedPreferences`.
- **Dependency & Maintenance Health**:
  - Updated Jackson Kotlin module to `2.18.2`.
  - Added Dependabot configuration (`.github/dependabot.yml`) for automated Gradle and GitHub Actions dependency updates.
  - Added "Updating NewPipeExtractor Checklist" in `CONTRIBUTING.md`.
- **Documentation Truthfulness**:
  - Added a comprehensive "Known Limitations & FAQ" section to `README.md`.
  - Clarified current progressive/HLS streaming support versus Stage 2 separate 1080p/4K DASH muxing and audio-track labeling.

---

## [1.0.0] - 2026-10-27

### Added
- **Core Streaming & Extraction**:
  - Integrated [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) v0.26.5 for parsing YouTube video info, manifests, and channel metadata without official API keys.
  - Progressive video streams (MP4/WebM) with frame rate indicators.
  - Adaptive HLS (`.m3u8`) streaming for YouTube Live broadcasts.
  - DASH manifest link extraction.
  - Subtitle track extraction including multi-lingual auto-generated and uploaded closed captions.
- **Dynamic Home Feeds & Subscriptions**:
  - Combined subscribed feed (`Subscribed: Latest Videos`) chronologically aggregating recent uploads from all subscribed channels.
  - Dedicated home screen row for each individual subscribed channel.
  - Preset dynamic feeds: Trending, Movies & Shows, Music, Gaming, Podcasts, and Live Streams.
- **In-App Settings UI**:
  - Custom Settings bottom sheet / dialog accessible via CloudStream plugin settings (⚙️ icon).
  - Subscription manager: Add channels by handle (`@username`), URL, or channel ID.
  - Row toggles: Enable or disable individual home feeds on demand.
- **Channel & Actor Integration**:
  - Uploader channel mapped as actor/person in CloudStream.
  - Channel page display listing uploaded videos as episodes for seamless binge-watching.
- **Performance & Networking**:
  - Custom `YoutubeDownloader` with connection pooling, gzip, and realistic browser headers.
  - Multi-page pagination support for search queries.
- **CI/CD & Repository Hygiene**:
  - Automated GitHub Actions build workflow to compile `.cs3` binaries and push manifests to the `builds` branch.
  - Added GNU General Public License v3.0 (`LICENSE`).
  - Added GitHub issue templates for bug reports and feature requests.
  - Added `CONTRIBUTING.md` guide and local build instructions.
