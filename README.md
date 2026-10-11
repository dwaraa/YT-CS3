# YouTube CloudStream 3 Provider

A high-performance **CloudStream 3** provider plugin for **YouTube**, powered by **NewPipeExtractor** and CloudStream's native streaming engine.

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
│       ├── YoutubeSettings.kt      # Interactive Android AlertDialog settings UI
│       └── YoutubeDownloader.kt    # Custom OkHttp Downloader for NewPipeExtractor
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

Compiled `.cs3` bundles are output in:
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
     https://raw.githubusercontent.com/dwaraa/YT-CS3/builds/repo.json
     ```
5. Tap **Download/Setup**.
6. Find **YouTube** and tap **Install**.
7. Tap the **Gear (⚙️) icon** next to YouTube to customize your subscribed channels and home screen rows!
