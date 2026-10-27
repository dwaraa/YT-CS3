# Contributing to YouTube CloudStream Provider

Thank you for your interest in contributing to the **YouTube CloudStream 3 Provider**! We welcome bug reports, feature suggestions, code contributions, and documentation improvements.

---

## 📋 Table of Contents
1. [Prerequisites](#prerequisites)
2. [Project Architecture](#project-architecture)
3. [Building Locally](#building-locally)
4. [Testing on an Android Device](#testing-on-an-android-device)
5. [Debugging & Logs](#debugging--logs)
6. [Updating NewPipeExtractor Checklist](#updating-newpipeextractor-checklist)
7. [Commit Message Conventions](#commit-message-conventions)
8. [Release & Version Tagging](#release--version-tagging)
9. [Submitting a Pull Request](#submitting-a-pull-request)

---

## 🛠️ Prerequisites

To build and develop the plugin locally, make sure you have installed:

- **Java Development Kit (JDK)**: JDK 17 or JDK 21 (JDK 17 recommended; Temurin, Zulu, or Oracle).
  - Verify with: `java -version`
- **Android SDK**:
  - `compileSdkVersion`: **35**
  - `minSdkVersion`: **21**
  - Set `ANDROID_HOME` or `ANDROID_SDK_ROOT` environment variable pointing to your Android SDK directory (e.g. `%LOCALAPPDATA%\Android\Sdk` on Windows or `~/Library/Android/sdk` on macOS).
- **Git**: Installed and configured.

---

## 🏛️ Project Architecture

The plugin is structured as an Android library utilizing the official CloudStream 3 Gradle plugin (`com.lagradost.cloudstream3.gradle`):

```
YouTube/
├── build.gradle.kts                 # Plugin manifest metadata (name, author, tvTypes, status)
├── icon.png                         # High-res square icon for CloudStream UI
└── src/main/kotlin/com/youtube/
    ├── YoutubePlugin.kt             # Plugin lifecycle entry point (@CloudstreamPlugin)
    ├── YoutubeProvider.kt           # MainAPI implementation: search, load, loadLinks, feeds
    ├── YoutubeDownloader.kt         # Custom OkHttp Downloader bridging NewPipeExtractor
    ├── YoutubePreferences.kt        # SharedPreferences wrapper for subscriptions & settings
    └── YoutubeSettings.kt           # Custom settings UI (Subscription manager & row toggles)
```

### Key Libraries
- **[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)**: Scrapes YouTube streams, channels, playlists, and metadata without proprietary Google Play Services or API quotas.
- **[NiceHttp](https://github.com/Blatzar/NiceHttp)** / **OkHttp**: High-efficiency HTTP client with gzip, HTTP/2, and connection pooling.
- **Jsoup**: HTML parsing and quick scraping where NewPipeExtractor isn't needed.

---

## 🔨 Building Locally

1. **Clone the repository**:
   ```bash
   git clone https://github.com/dwaraa/YT-CS3.git
   cd YT-CS3
   ```

2. **Build the plugin bundle (`.cs3`)**:
   - On Linux / macOS:
     ```bash
     ./gradlew make
     ```
   - On Windows PowerShell / Command Prompt:
     ```powershell
     .\gradlew.bat make
     ```

3. **Generate repository plugins metadata (`plugins.json`)**:
   ```bash
   ./gradlew makePluginsJson
   ```

4. **Clean and rebuild**:
   ```bash
   ./gradlew clean make --stacktrace
   ```

The compiled plugin bundle will be generated at:
```
YouTube/build/YouTube.cs3
```

---

## 📱 Testing on an Android Device

### Option A: Direct File Installation
1. Connect your Android device or emulator with USB debugging enabled.
2. Push the built `.cs3` file to your device:
   ```bash
   adb push YouTube/build/YouTube.cs3 /sdcard/Download/
   ```
3. In CloudStream 3, navigate to **Settings** > **Extensions** > **Install from file** (or open the `.cs3` file with CloudStream via any file manager app).

### Option B: Local HTTP Server
1. Serve the `dist/` or `YouTube/build/` folder using Python:
   ```bash
   python -m http.server 8080
   ```
2. Add your computer's local IP (e.g., `http://192.168.1.50:8080/repo.json`) as a repository in CloudStream.

---

## 🔍 Debugging & Logs

To monitor plugin activity and troubleshoot extraction errors, inspect `logcat`:

```bash
adb logcat -v time -s "YoutubeProvider" "Cloudstream" "System.err"
```

To filter for Kotlin stacktraces and exceptions:
```bash
adb logcat *:E | grep -i youtube
```

---

## 🔄 Updating NewPipeExtractor Checklist

Because YouTube regularly iterates on internal player responses, throttling parameters (`n-sig`), and consent/bot hurdles, updating [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) is a critical routine maintenance task.

Follow this checklist whenever updating NewPipeExtractor:

1. **Check Upstream Releases & JitPack**:
   - Visit [TeamNewPipe/NewPipeExtractor Releases / Tags](https://github.com/TeamNewPipe/NewPipeExtractor/tags).
   - Check [JitPack Build Log](https://jitpack.io/#com.github.teamnewpipe/NewPipeExtractor) to verify that the release or git commit built successfully (shows green `ok`).
2. **Update Root Gradle Configuration**:
   - In `build.gradle.kts`, update the dependency tag:
     ```kotlin
     implementation("com.github.teamnewpipe:NewPipeExtractor:v0.X.Y")
     ```
3. **Verify API Compatibility & Method Signatures**:
   - Run `./gradlew clean make` to detect any upstream API breaks in:
     - `Downloader` / `Request` / `Response` contracts (`YoutubeDownloader.kt`).
     - Extractor retrieval: `StreamInfo.getInfo(...)`, `service.getChannelExtractor(...)`, `service.kioskList`.
     - Exception classes under `org.schabi.newpipe.extractor.exceptions.*`.
4. **Smoke-Test Core Features on a Device or Emulator**:
   - [ ] **Progressive Stream Playback**: Open standard 720p/1080p videos; verify audio and video play synchronously.
   - [ ] **Live Streams**: Open a live event or 24/7 news stream; verify adaptive HLS playlist starts immediately.
   - [ ] **Home Screen Feeds**: Verify Trending, Music, Gaming, and Subscribed rows populate.
   - [ ] **Channel Pages**: Click an uploader/actor card and verify videos populate as playable episodes.
   - [ ] **Search & Pagination**: Search queries with multi-page results.
   - [ ] **Subtitles**: Verify English / Auto-generated subtitles load.
5. **Update Documentation & Open PR**:
   - Document the update in `CHANGELOG.md`.
   - Submit a pull request titled: `chore(deps): update NewPipeExtractor to <version>`.

---

## ✍️ Commit Message Conventions

We follow the **[Conventional Commits](https://www.conventionalcommits.org/)** specification:

```
<type>(<scope>): <short description>

[optional body]

[optional footer(s)]
```

### Common Types:
- `feat`: A new feature (e.g. `feat(stream): add DASH audio-only tracks`)
- `fix`: A bug fix (e.g. `fix(extractor): resolve 403 on HLS live streams`)
- `docs`: Documentation updates (e.g. `docs(readme): add installation instructions`)
- `refactor`: Code reorganization that neither fixes a bug nor adds a feature
- `perf`: Performance improvement (e.g. `perf(network): enable OkHttp connection pool`)
- `chore`: Maintenance tasks, dependency bumps, or Gradle configuration

---

## 🏷️ Release & Version Tagging

1. Update `version` in `YouTube/build.gradle.kts` (integer increment).
2. Document user-facing changes in `CHANGELOG.md` under a new `[X.Y.Z]` header following [Keep a Changelog](https://keepachangelog.com/).
3. Commit the changes:
   ```bash
   git commit -m "chore(release): bump version to v1.1.0"
   ```
4. Create an annotated Git tag:
   ```bash
   git tag -a v1.1.0 -m "Release v1.1.0"
   git push origin master --tags
   ```

---

## 🚀 Submitting a Pull Request

1. **Fork** the repository and create your feature branch:
   ```bash
   git checkout -b feat/your-feature-name
   ```
2. Make your changes adhering to Kotlin coding conventions and CloudStream plugin best practices.
3. Verify that the build succeeds without warnings or errors:
   ```bash
   ./gradlew clean make
   ```
4. Test the generated `.cs3` on a device or emulator.
5. Update `CHANGELOG.md` under `[Unreleased]`.
6. Push to your branch and open a Pull Request against `master`.
7. Fill out the PR description with screenshots, log outputs, and motivation for the change.
