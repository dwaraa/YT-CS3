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
  - **➕ Add Subscribed Channel**: Enter any channel handle (e.g., `@MrBeast`, `@mkbhd`), channel URL, or channel ID. The plugin resolves channel details and icons automatically.
  - **📋 Manage Subscriptions**: View and delete subscribed channels.
  - **🎛️ Customize Home Rows**: Toggle any row on or off:
    - *Combined Subscriptions Feed*
    - *Individual Channel Rows*
    - *Trending*
    - *Movies & Shows*
    - *Music*
    - *Gaming*
    - *Podcasts*
    - *Live Streams*
  - **🔄 Reset to Defaults**: Restore default configuration.

### 👤 Channel as Actor / Person Integration
- **Actors / Channels**: Videos link the uploader as a person/channel with their avatar.
- Clicking on a channel or searching by channel name/handle immediately opens the channel page, showing all uploaded videos as playable episodes.

### 🎵 Detailed Audio Tracks & Language Labels
- **Multi-Language Audio**: Distinguishes original vs. dubbed audio tracks (e.g., `YouTube Audio - English [ORIGINAL] (160kbps M4A)` or `YouTube Audio - Spanish [DUBBED] (128kbps M4A)`).
- **Video Qualities & Codecs**: Clear resolution tags with frame rates and codecs (e.g., `1080p 60fps (MP4)`, `4K`, `720p`).
- **Subtitles & Closed Captions**: Full language display names with auto-generated indicators (e.g., `English (Auto-generated)`, `Spanish`).
- **Live Streams**: Adaptive HLS `.m3u8` master playlists for live events.
- **DASH Manifests**: Multi-audio DASH support.

---

## 📁 Project Structure

```
YT-CS3/
├── .github/
│   ├── ISSUE_TEMPLATE/
│   │   ├── bug_report.md           # Bug report template with logcat & versions
│   │   └── feature_request.md      # Feature request template
│   └── workflows/
│       └── build.yml               # Automated GitHub Actions CI/CD to build & publish .cs3 files
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── YouTube/
│   ├── build.gradle.kts            # Cloudstream plugin metadata & settings
│   ├── icon.png                    # High-res YouTube provider icon
│   └── src/main/kotlin/com/youtube/
│       ├── YoutubePlugin.kt        # @CloudstreamPlugin entry point with openSettings hook
│       ├── YoutubeProvider.kt      # MainAPI implementation with dynamic feeds & extractors
│       ├── YoutubePreferences.kt   # Persistent subscription & row toggle storage
│       ├── YoutubeSettings.kt      # Interactive Android AlertDialog/BottomSheet settings UI
│       └── YoutubeDownloader.kt    # Custom OkHttp Downloader for NewPipeExtractor
├── build.gradle.kts                # Root Gradle configuration
├── settings.gradle.kts             # Gradle multi-project settings
├── gradle.properties               # JVM and compiler tuning
├── repo.json                       # Cloudstream repository manifest
├── CHANGELOG.md                    # Changelog adhering to Keep a Changelog
├── CONTRIBUTING.md                 # Contribution guidelines & developer setup
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

For comprehensive build prerequisites, developer instructions, and debugging tips, see [CONTRIBUTING.md](CONTRIBUTING.md).

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
