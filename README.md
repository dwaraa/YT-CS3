# YouTube CloudStream 3 Provider

A high-performance **CloudStream 3** provider plugin for **YouTube**, powered by **NewPipeExtractor** and CloudStream's native streaming engine.

---

## 🚀 Features

- **📺 Rich Home Screen Categories**:
  - **Trending** (Popular trending videos)
  - **Movies & Shows** (`trending_movies_and_shows`)
  - **Music** (`trending_music`)
  - **Gaming** (`trending_gaming`)
  - **Podcasts** (`trending_podcasts_episodes`)
  - **Live Streams** (`live`)
- **🔍 Full Search & Quick Search**:
  - Instant query search with cursor pagination
  - Video cards with high-resolution thumbnails and video durations
- **🎬 Multiple Content Types Supported**:
  - **Single Videos**: Standard `watch?v=...`, `youtu.be/...`, shorts (`/shorts/...`), and embed links
  - **Channels**: Channel page loading (`/@username`, `/channel/UC...`, `/c/...`) showing uploaded videos as episodes
  - **Playlists**: Full playlist resolution (`/playlist?list=...`) with sequential episodes
  - **Live Streams**: Native HLS playback for YouTube live streams
- **⚡ Dual-Engine Stream Extraction**:
  - Primary resolution through CloudStream's native YouTube extractor
  - Automatic fallback through NewPipeExtractor for progressive MP4 streams, DASH, and HLS `.m3u8` streams
  - Multi-language subtitle & closed captions support

---

## 📁 Project Structure

```
YT-CS3/
├── .github/
│   └── workflows/
│       └── build.yml               # Automated GitHub Actions CI/CD to build & publish .cs3 files
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── YouTube/
│   ├── build.gradle.kts            # Cloudstream plugin metadata & settings
│   ├── icon.png                    # High-res YouTube provider icon
│   └── src/
│       └── main/
│           └── kotlin/
│               └── com/
│                   └── youtube/
│                       ├── YoutubePlugin.kt     # Plugin entry point
│                       ├── YoutubeProvider.kt   # Provider implementation (MainAPI)
│                       └── YoutubeDownloader.kt # NewPipeExtractor Downloader implementation
├── build.gradle.kts                # Root Gradle configuration
├── settings.gradle.kts             # Gradle multi-project settings
├── gradle.properties               # JVM and compiler tuning
├── repo.json                       # Cloudstream repository manifest
└── README.md                       # Documentation
```

---

## 🛠️ Building Locally

To build the plugin `.cs3` artifact locally, run:

```bash
./gradlew make
```

To generate `plugins.json`:

```bash
./gradlew makePluginsJson
```

Compiled `.cs3` bundles will be output in:
```
YouTube/build/YouTube.cs3
```

---

## 📲 Installing in CloudStream 3

1. Open **CloudStream 3**.
2. Navigate to **Settings** > **Extensions**.
3. Tap **Add Repository**.
4. Enter the repository details:
   - **Repository Name**: `YouTube Provider`
   - **Repository URL**:
     ```
     https://raw.githubusercontent.com/<YOUR_GITHUB_USER>/YT-CS3/builds/repo.json
     ```
5. Tap **Download/Setup**.
6. Find **YouTube** and tap **Install**.
