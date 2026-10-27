# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Planned
- Search filters (sort by date, duration, upload date, view count).
- Playlist browsing and playback.
- SponsorBlock integration for skipping sponsor segments.
- Return YouTube Dislike (RYD) API integration.

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
