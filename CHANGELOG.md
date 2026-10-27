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
- Chapter markers and playback resume points.

---

## [1.0.0] - 2026-10-27

### Added
- **Core Streaming & Extraction**:
  - Integrated [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) v0.26.5 for parsing YouTube video info, manifests, and channel metadata without official API keys.
  - High-resolution video extraction (up to 4K / 1080p60) with codec indicators (`MP4`, `WebM`).
  - Multi-language audio stream support with explicit `[ORIGINAL]` and `[DUBBED]` track labeling.
  - Adaptive HLS (`.m3u8`) streaming for YouTube Live broadcasts.
  - DASH manifest parsing for multi-stream playback.
  - Subtitle track extraction including multi-lingual auto-generated and uploaded closed captions.
- **Dynamic Home Feeds & Subscriptions**:
  - Combined subscribed feed (`Subscribed: Latest Videos`) chronologically aggregating recent uploads from all subscribed channels.
  - Dedicated home screen row for each individual subscribed channel.
  - Preset dynamic feeds: Trending, Movies & Shows, Music, Gaming, Podcasts, and Live Streams.
- **In-App Settings UI**:
  - Custom Settings bottom sheet / dialog accessible via CloudStream plugin settings (⚙️ icon).
  - Subscription manager: Add channels by handle (`@username`), URL, or channel ID with auto-resolution of channel title and avatar.
  - Row toggles: Enable or disable individual home feeds on demand.
  - Reset to default settings option.
- **Channel & Actor Integration**:
  - Uploader channel mapped as actor/person in CloudStream.
  - Channel page display listing uploaded videos as episodes for seamless binge-watching.
- **Performance & Networking**:
  - Custom `YoutubeDownloader` based on `NiceHttp` / `OkHttpClient` with connection pooling, gzip, and realistic browser headers.
  - Multi-page pagination support for search queries.
- **CI/CD & Repository Hygiene**:
  - Automated GitHub Actions build workflow to compile `.cs3` binaries and push manifests to the `builds` branch.
  - Added GNU General Public License v3.0 (`LICENSE`).
  - Added GitHub issue templates for bug reports and feature requests.
  - Added `CONTRIBUTING.md` guide and local build instructions.
