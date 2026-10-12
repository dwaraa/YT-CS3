package com.youtube

/**
 * Embedded single-page web app for YouTube extension settings.
 * Styled with CloudStream's signature dark aesthetic, featuring:
 * - Fluid responsive layout (Phone, Tablet, Desktop, Android TV)
 * - Playback & video resolution selectors
 * - Live channel subscriptions manager with NewPipe extractor
 * - Interactive home feed toggle switches
 * - Remote access header with live local IP & port
 * - Instant auto-save with toast notifications
 */
object YoutubeWebPage {

    fun getHtml(localIp: String, port: Int): String {
        val serverUrl = "http://$localIp:$port"
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>YouTube Settings • CloudStream</title>
    <style>
        :root {
            --bg-base: #0B0C10;
            --bg-sheet: #111218;
            --bg-card: #181A24;
            --bg-card-inner: #13141C;
            --border-card: #262836;
            --border-subtle: #212330;
            --primary: #3D50FA;
            --primary-hover: #5364FF;
            --primary-light: #8C9EFF;
            --primary-tint: rgba(61, 80, 250, 0.16);
            --yt-red: #E50914;
            --yt-red-tint: rgba(229, 9, 20, 0.16);
            --danger: #FF5252;
            --danger-bg: #2B161B;
            --success: #10B981;
            --text-primary: #F1F2F6;
            --text-secondary: #9296A6;
            --text-muted: #646878;
            --radius-card: 16px;
            --radius-sm: 10px;
            --radius-pill: 9999px;
            --transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            -webkit-tap-highlight-color: transparent;
        }

        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            background-color: var(--bg-base);
            color: var(--text-primary);
            line-height: 1.5;
            padding: 16px;
            padding-bottom: 40px;
            max-width: 760px;
            margin: 0 auto;
            background-image: radial-gradient(circle at 100% 0%, rgba(61, 80, 250, 0.08) 0%, transparent 400px),
                              radial-gradient(circle at 0% 100%, rgba(229, 9, 20, 0.05) 0%, transparent 400px);
            background-attachment: fixed;
        }

        /* Top Header */
        .header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 16px;
            padding-bottom: 12px;
            border-bottom: 1px solid var(--border-subtle);
        }

        .header-brand {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .logo-box {
            width: 40px;
            height: 40px;
            background: linear-gradient(135deg, #FF1744, #C62828);
            border-radius: 12px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 18px;
            color: white;
            box-shadow: 0 4px 12px rgba(229, 9, 20, 0.3);
        }

        .header-title h1 {
            font-size: 18px;
            font-weight: 700;
            color: var(--text-primary);
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .header-title p {
            font-size: 12px;
            color: var(--text-secondary);
        }

        .status-pill {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            background: var(--bg-card);
            border: 1px solid var(--border-card);
            padding: 5px 12px;
            border-radius: var(--radius-pill);
            font-size: 11.5px;
            color: var(--text-secondary);
            font-weight: 500;
        }

        .status-dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background-color: var(--success);
            box-shadow: 0 0 8px var(--success);
        }

        /* Remote Access Callout */
        .remote-banner {
            background: linear-gradient(135deg, #171A29, #131520);
            border: 1px solid rgba(61, 80, 250, 0.3);
            border-radius: var(--radius-card);
            padding: 14px 16px;
            margin-bottom: 20px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            flex-wrap: wrap;
            gap: 12px;
        }

        .remote-info {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .remote-icon {
            font-size: 24px;
        }

        .remote-text h3 {
            font-size: 13.5px;
            font-weight: 600;
            color: var(--primary-light);
        }

        .remote-text p {
            font-size: 11.5px;
            color: var(--text-secondary);
            font-family: monospace;
            margin-top: 2px;
        }

        .btn-copy {
            background: var(--primary);
            color: white;
            border: none;
            padding: 8px 14px;
            border-radius: var(--radius-sm);
            font-size: 12px;
            font-weight: 600;
            cursor: pointer;
            transition: var(--transition);
            display: inline-flex;
            align-items: center;
            gap: 6px;
        }

        .btn-copy:hover {
            background: var(--primary-hover);
            transform: translateY(-1px);
        }

        /* Section Cards */
        .section-header {
            display: flex;
            align-items: center;
            gap: 8px;
            margin-top: 20px;
            margin-bottom: 8px;
            padding: 0 4px;
        }

        .section-header span {
            font-size: 14px;
        }

        .section-header h2 {
            font-size: 11.5px;
            font-weight: 700;
            color: var(--primary-light);
            letter-spacing: 0.08em;
            text-transform: uppercase;
        }

        .card {
            background: var(--bg-card);
            border: 1px solid var(--border-card);
            border-radius: var(--radius-card);
            overflow: hidden;
            box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
        }

        .card-inner-padding {
            padding: 16px;
        }

        .row-item {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 14px 16px;
            border-bottom: 1px solid var(--border-subtle);
            transition: var(--transition);
        }

        .row-item:last-child {
            border-bottom: none;
        }

        .row-item:hover {
            background: rgba(255, 255, 255, 0.02);
        }

        .row-left {
            display: flex;
            align-items: center;
            gap: 14px;
            flex: 1;
            padding-right: 12px;
        }

        .squircle-icon {
            width: 38px;
            height: 38px;
            border-radius: 12px;
            background: var(--bg-card-inner);
            border: 1px solid var(--border-card);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 17px;
            flex-shrink: 0;
        }

        .row-text h4 {
            font-size: 14px;
            font-weight: 600;
            color: var(--text-primary);
        }

        .row-text p {
            font-size: 11.5px;
            color: var(--text-secondary);
            margin-top: 1px;
        }

        /* Select Control */
        .select-styled {
            background: var(--primary-tint);
            border: 1px solid var(--primary);
            color: var(--primary-light);
            font-size: 12.5px;
            font-weight: 600;
            padding: 7px 12px;
            border-radius: 12px;
            cursor: pointer;
            outline: none;
            transition: var(--transition);
        }

        .select-styled:hover {
            background: rgba(61, 80, 250, 0.28);
        }

        .select-styled option {
            background: var(--bg-card);
            color: var(--text-primary);
        }

        /* Modern Switch */
        .switch {
            position: relative;
            display: inline-block;
            width: 44px;
            height: 24px;
            flex-shrink: 0;
        }

        .switch input {
            opacity: 0;
            width: 0;
            height: 0;
        }

        .slider {
            position: absolute;
            cursor: pointer;
            top: 0; left: 0; right: 0; bottom: 0;
            background-color: #2A2D3C;
            transition: var(--transition);
            border-radius: 24px;
        }

        .slider:before {
            position: absolute;
            content: "";
            height: 18px;
            width: 18px;
            left: 3px;
            bottom: 3px;
            background-color: #8E92A4;
            transition: var(--transition);
            border-radius: 50%;
        }

        input:checked + .slider {
            background-color: var(--primary);
        }

        input:checked + .slider:before {
            transform: translateX(20px);
            background-color: #FFFFFF;
        }

        /* Channel Subscriptions */
        .input-group {
            display: flex;
            gap: 8px;
            margin-bottom: 10px;
        }

        .input-text {
            flex: 1;
            background: var(--bg-card-inner);
            border: 1px solid var(--border-card);
            border-radius: 12px;
            padding: 10px 14px;
            font-size: 13.5px;
            color: var(--text-primary);
            outline: none;
            transition: var(--transition);
        }

        .input-text:focus {
            border-color: var(--primary);
            box-shadow: 0 0 0 3px rgba(61, 80, 250, 0.2);
        }

        .btn-add {
            background: var(--primary);
            color: white;
            border: none;
            border-radius: 12px;
            padding: 0 18px;
            font-size: 13px;
            font-weight: 700;
            cursor: pointer;
            transition: var(--transition);
            display: inline-flex;
            align-items: center;
            gap: 6px;
        }

        .btn-add:hover {
            background: var(--primary-hover);
        }

        .btn-add:disabled {
            opacity: 0.6;
            cursor: not-allowed;
        }

        .channel-hint {
            font-size: 11.5px;
            color: var(--text-muted);
            margin-bottom: 14px;
        }

        .channel-list {
            display: flex;
            flex-direction: column;
            gap: 8px;
        }

        .channel-item {
            display: flex;
            align-items: center;
            justify-content: space-between;
            background: var(--bg-card-inner);
            border: 1px solid var(--border-card);
            border-radius: 14px;
            padding: 10px 12px;
            transition: var(--transition);
        }

        .channel-item:hover {
            border-color: rgba(61, 80, 250, 0.4);
        }

        .channel-profile {
            display: flex;
            align-items: center;
            gap: 12px;
            overflow: hidden;
        }

        .channel-avatar {
            width: 40px;
            height: 40px;
            border-radius: 50%;
            background: linear-gradient(135deg, var(--primary), #8C9EFF);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 13px;
            font-weight: 700;
            color: white;
            overflow: hidden;
            flex-shrink: 0;
        }

        .channel-avatar img {
            width: 100%;
            height: 100%;
            object-fit: cover;
        }

        .channel-meta h5 {
            font-size: 13.5px;
            font-weight: 600;
            color: var(--text-primary);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .channel-meta p {
            font-size: 11.5px;
            color: var(--text-secondary);
        }

        .btn-delete {
            background: var(--danger-bg);
            border: 1px solid rgba(255, 82, 82, 0.2);
            color: var(--danger);
            width: 32px;
            height: 32px;
            border-radius: 10px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 14px;
            cursor: pointer;
            transition: var(--transition);
            flex-shrink: 0;
        }

        .btn-delete:hover {
            background: rgba(255, 82, 82, 0.25);
            transform: scale(1.05);
        }

        .empty-state {
            text-align: center;
            padding: 24px 16px;
            color: var(--text-muted);
        }

        .empty-state .icon {
            font-size: 32px;
            margin-bottom: 6px;
        }

        /* Action Buttons */
        .btn-action {
            background: var(--bg-card-inner);
            border: 1px solid var(--border-card);
            color: var(--text-primary);
            padding: 7px 14px;
            border-radius: 10px;
            font-size: 12.5px;
            font-weight: 600;
            cursor: pointer;
            transition: var(--transition);
        }

        .btn-action:hover {
            background: var(--primary);
            border-color: var(--primary);
            color: white;
        }

        .btn-danger {
            background: var(--danger-bg);
            border: 1px solid rgba(255, 82, 82, 0.25);
            color: var(--danger);
        }

        .btn-danger:hover {
            background: var(--danger);
            border-color: var(--danger);
            color: white;
        }

        /* Toast notification */
        .toast {
            position: fixed;
            bottom: 24px;
            left: 50%;
            transform: translateX(-50%) translateY(100px);
            background: #1F2232;
            border: 1px solid var(--primary);
            color: white;
            padding: 10px 20px;
            border-radius: var(--radius-pill);
            font-size: 13px;
            font-weight: 600;
            box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
            display: flex;
            align-items: center;
            gap: 8px;
            opacity: 0;
            transition: all 0.3s cubic-bezier(0.18, 0.89, 0.32, 1.28);
            z-index: 1000;
            pointer-events: none;
        }

        .toast.show {
            transform: translateX(-50%) translateY(0);
            opacity: 1;
        }

        /* Footer */
        .footer {
            text-align: center;
            font-size: 11.5px;
            color: var(--text-muted);
            margin-top: 28px;
        }

        .spinner {
            display: inline-block;
            width: 14px;
            height: 14px;
            border: 2px solid rgba(255,255,255,0.3);
            border-radius: 50%;
            border-top-color: #fff;
            animation: spin 0.8s ease-in-out infinite;
        }

        @keyframes spin {
            to { transform: rotate(360deg); }
        }
    </style>
</head>
<body>

    <!-- Header -->
    <header class="header">
        <div class="header-brand">
            <div class="logo-box">▶</div>
            <div class="header-title">
                <h1>YouTube Settings</h1>
                <p>CloudStream 3 Extension</p>
            </div>
        </div>
        <div class="status-pill">
            <div class="status-dot"></div>
            <span>Connected</span>
        </div>
    </header>

    <!-- Remote Access Callout -->
    <div class="remote-banner">
        <div class="remote-info">
            <div class="remote-icon">🌐</div>
            <div class="remote-text">
                <h3>Remote Web Management</h3>
                <p id="remoteUrlDisplay">$serverUrl</p>
            </div>
        </div>
        <button class="btn-copy" onclick="copyRemoteUrl()">
            <span id="copyIcon">📋</span>
            <span id="copyText">Copy URL</span>
        </button>
    </div>

    <!-- Playback Section -->
    <div class="section-header">
        <span>🎬</span>
        <h2>Playback & Streaming</h2>
    </div>
    <div class="card">
        <div class="row-item">
            <div class="row-left">
                <div class="squircle-icon">📺</div>
                <div class="row-text">
                    <h4>Preferred Video Quality</h4>
                    <p>Default streaming resolution for video streams</p>
                </div>
            </div>
            <select id="preferredQuality" class="select-styled" onchange="saveSetting('preferredQuality', this.value)">
                <option value="Auto">Auto (Best)</option>
                <option value="2160p">2160p (4K)</option>
                <option value="1440p">1440p (2K)</option>
                <option value="1080p">1080p (Full HD)</option>
                <option value="720p">720p (HD)</option>
                <option value="480p">480p</option>
                <option value="360p">360p</option>
            </select>
        </div>
        <div class="row-item">
            <div class="row-left">
                <div class="squircle-icon">💬</div>
                <div class="row-text">
                    <h4>Preferred Subtitles</h4>
                    <p>Auto-load matching subtitle language track</p>
                </div>
            </div>
            <select id="preferredSubtitle" class="select-styled" onchange="saveSetting('preferredSubtitle', this.value)">
                <option value="None">None</option>
                <option value="English">English</option>
                <option value="Spanish">Spanish</option>
                <option value="Tamil">Tamil</option>
                <option value="Hindi">Hindi</option>
                <option value="Arabic">Arabic</option>
                <option value="French">French</option>
                <option value="German">German</option>
                <option value="Japanese">Japanese</option>
                <option value="Auto">Auto</option>
            </select>
        </div>
    </div>

    <!-- Channel Subscriptions Section -->
    <div class="section-header">
        <span>📺</span>
        <h2>Subscribed Channels (<span id="channelCount">0</span>)</h2>
    </div>
    <div class="card card-inner-padding">
        <div class="input-group">
            <input type="text" id="channelInput" class="input-text" placeholder="@handle, channel URL, or channel ID" onkeydown="if(event.key==='Enter') addChannel()">
            <button id="addChannelBtn" class="btn-add" onclick="addChannel()">+ Add</button>
        </div>
        <p class="channel-hint">Subscribed creators appear as feeds directly on your CloudStream home screen.</p>
        <div id="channelList" class="channel-list">
            <!-- Dynamically populated -->
        </div>
    </div>

    <!-- Home Feed Customization Section -->
    <div class="section-header">
        <span>🎛</span>
        <h2>Home Screen Feeds</h2>
    </div>
    <div class="card">
        <div class="row-item">
            <div class="row-left">
                <div class="squircle-icon">🔔</div>
                <div class="row-text">
                    <h4>Combined Subscriptions Feed</h4>
                    <p>Merge all your subscriptions into one unified feed</p>
                </div>
            </div>
            <label class="switch">
                <input type="checkbox" id="showCombinedSubscriptions" onchange="saveToggle('showCombinedSubscriptions', this.checked)">
                <span class="slider"></span>
            </label>
        </div>
        <div class="row-item">
            <div class="row-left">
                <div class="squircle-icon">👥</div>
                <div class="row-text">
                    <h4>Individual Channel Rows</h4>
                    <p>Separate dedicated row for each subscribed creator</p>
                </div>
            </div>
            <label class="switch">
                <input type="checkbox" id="showIndividualChannelRows" onchange="saveToggle('showIndividualChannelRows', this.checked)">
                <span class="slider"></span>
            </label>
        </div>
        <div class="row-item">
            <div class="row-left">
                <div class="squircle-icon">🔥</div>
                <div class="row-text">
                    <h4>Trending Feed</h4>
                    <p>Viral and popular videos trending worldwide</p>
                </div>
            </div>
            <label class="switch">
                <input type="checkbox" id="showTrending" onchange="saveToggle('showTrending', this.checked)">
                <span class="slider"></span>
            </label>
        </div>
        <div class="row-item">
            <div class="row-left">
                <div class="squircle-icon">🎬</div>
                <div class="row-text">
                    <h4>Movies & TV Shows</h4>
                    <p>Featured free YouTube movies and full episodes</p>
                </div>
            </div>
            <label class="switch">
                <input type="checkbox" id="showMovies" onchange="saveToggle('showMovies', this.checked)">
                <span class="slider"></span>
            </label>
        </div>
        <div class="row-item">
            <div class="row-left">
                <div class="squircle-icon">🎵</div>
                <div class="row-text">
                    <h4>Music Feed</h4>
                    <p>Trending songs, music videos, and artists</p>
                </div>
            </div>
            <label class="switch">
                <input type="checkbox" id="showMusic" onchange="saveToggle('showMusic', this.checked)">
                <span class="slider"></span>
            </label>
        </div>
        <div class="row-item">
            <div class="row-left">
                <div class="squircle-icon">🎮</div>
                <div class="row-text">
                    <h4>Gaming Feed</h4>
                    <p>Gaming livestreams, clips, and highlights</p>
                </div>
            </div>
            <label class="switch">
                <input type="checkbox" id="showGaming" onchange="saveToggle('showGaming', this.checked)">
                <span class="slider"></span>
            </label>
        </div>
        <div class="row-item">
            <div class="row-left">
                <div class="squircle-icon">🎙</div>
                <div class="row-text">
                    <h4>Podcasts Feed</h4>
                    <p>Full length video podcasts and interviews</p>
                </div>
            </div>
            <label class="switch">
                <input type="checkbox" id="showPodcasts" onchange="saveToggle('showPodcasts', this.checked)">
                <span class="slider"></span>
            </label>
        </div>
        <div class="row-item">
            <div class="row-left">
                <div class="squircle-icon">🔴</div>
                <div class="row-text">
                    <h4>Live Streams Feed</h4>
                    <p>Real-time live broadcasts and active streams</p>
                </div>
            </div>
            <label class="switch">
                <input type="checkbox" id="showLive" onchange="saveToggle('showLive', this.checked)">
                <span class="slider"></span>
            </label>
        </div>
    </div>

    <!-- Maintenance Section -->
    <div class="section-header">
        <span>⚙</span>
        <h2>Maintenance & Actions</h2>
    </div>
    <div class="card">
        <div class="row-item">
            <div class="row-left">
                <div class="squircle-icon">⚡</div>
                <div class="row-text">
                    <h4>Clear Feed Cache</h4>
                    <p>Wipe in-memory feed cache to reload fresh content</p>
                </div>
            </div>
            <button class="btn-action" onclick="clearFeedCache()">Clear Cache</button>
        </div>
        <div class="row-item">
            <div class="row-left">
                <div class="squircle-icon">🔄</div>
                <div class="row-text">
                    <h4>Reset Settings</h4>
                    <p>Restore default feed layout and subscribed channels</p>
                </div>
            </div>
            <button class="btn-action btn-danger" onclick="resetDefaults()">Reset</button>
        </div>
    </div>

    <!-- Toast Notification -->
    <div id="toast" class="toast">
        <span id="toastIcon">✓</span>
        <span id="toastMsg">Saved</span>
    </div>

    <!-- Footer -->
    <footer class="footer">
        <p>CloudStream 3 • YouTube Plugin • Powered by NewPipe Extractor</p>
    </footer>

    <script>
        const API_BASE = window.location.origin;

        function showToast(msg, isSuccess = true) {
            const toast = document.getElementById('toast');
            const msgEl = document.getElementById('toastMsg');
            const iconEl = document.getElementById('toastIcon');
            msgEl.textContent = msg;
            iconEl.textContent = isSuccess ? '✓' : 'ℹ';
            toast.classList.add('show');
            setTimeout(() => {
                toast.classList.remove('show');
            }, 2200);
        }

        function copyRemoteUrl() {
            const url = document.getElementById('remoteUrlDisplay').textContent.trim();
            navigator.clipboard.writeText(url).then(() => {
                document.getElementById('copyText').textContent = 'Copied!';
                document.getElementById('copyIcon').textContent = '✓';
                showToast('URL copied to clipboard!');
                setTimeout(() => {
                    document.getElementById('copyText').textContent = 'Copy URL';
                    document.getElementById('copyIcon').textContent = '📋';
                }, 2000);
            }).catch(() => {
                showToast(url);
            });
        }

        async function loadSettings() {
            try {
                const res = await fetch(API_BASE + '/api/settings');
                if (!res.ok) return;
                const data = await res.json();

                if (data.serverUrl) {
                    document.getElementById('remoteUrlDisplay').textContent = data.serverUrl;
                }

                // Selects
                document.getElementById('preferredQuality').value = data.preferredQuality || '1080p';
                document.getElementById('preferredSubtitle').value = data.preferredSubtitle || 'English';

                // Toggles
                const toggles = [
                    'showCombinedSubscriptions', 'showIndividualChannelRows',
                    'showTrending', 'showMovies', 'showMusic', 'showGaming',
                    'showPodcasts', 'showLive'
                ];
                toggles.forEach(key => {
                    const el = document.getElementById(key);
                    if (el && data[key] !== undefined) {
                        el.checked = Boolean(data[key]);
                    }
                });

                // Channels
                renderChannels(data.channels || []);
            } catch (err) {
                console.error('Failed to load settings', err);
            }
        }

        function renderChannels(channels) {
            document.getElementById('channelCount').textContent = channels.length;
            const container = document.getElementById('channelList');
            container.innerHTML = '';

            if (channels.length === 0) {
                container.innerHTML = `
                    <div class="empty-state">
                        <div class="icon">📺</div>
                        <p>No subscribed channels yet. Type a handle above to add one.</p>
                    </div>
                `;
                return;
            }

            channels.forEach(ch => {
                const item = document.createElement('div');
                item.className = 'channel-item';

                const initials = (ch.name || 'YT').substring(0, 2).toUpperCase();
                const avatarHtml = ch.avatarUrl 
                    ? '<img src="' + escapeHtml(ch.avatarUrl) + '" alt="" onerror="this.style.display=\'none\';">'
                    : initials;

                const handle = (ch.url || '').split('/').pop() || ch.id;

                item.innerHTML = '<div class="channel-profile">' +
                    '<div class="channel-avatar">' + avatarHtml + '</div>' +
                    '<div class="channel-meta">' +
                        '<h5>' + escapeHtml(ch.name) + '</h5>' +
                        '<p>' + escapeHtml(handle) + ' • YouTube</p>' +
                    '</div>' +
                '</div>' +
                '<button class="btn-delete" title="Remove channel" onclick="removeChannel(\'' + encodeURIComponent(ch.url) + '\')">✕</button>';
                container.appendChild(item);
            });
        }

        function escapeHtml(str) {
            return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
        }

        async function saveSetting(key, value) {
            try {
                const payload = {};
                payload[key] = value;
                const res = await fetch(API_BASE + '/api/settings', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                if (res.ok) {
                    showToast('Quality / Subtitles saved!');
                }
            } catch (err) {
                showToast('Failed to save', false);
            }
        }

        async function saveToggle(key, isChecked) {
            try {
                const payload = {};
                payload[key] = isChecked;
                const res = await fetch(API_BASE + '/api/settings', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                if (res.ok) {
                    showToast('Feed preference updated!');
                }
            } catch (err) {
                showToast('Failed to save', false);
            }
        }

        async function addChannel() {
            const input = document.getElementById('channelInput');
            const val = input.value.trim();
            if (!val) return;

            const btn = document.getElementById('addChannelBtn');
            btn.disabled = true;
            btn.innerHTML = '<span class="spinner"></span>';

            try {
                const res = await fetch(API_BASE + '/api/channels/add', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ input: val })
                });
                if (res.ok) {
                    input.value = '';
                    const data = await res.json();
                    renderChannels(data.channels || []);
                    showToast('Channel added!');
                } else {
                    showToast('Failed to resolve channel', false);
                }
            } catch (err) {
                showToast('Network error', false);
            } finally {
                btn.disabled = false;
                btn.innerHTML = '+ Add';
            }
        }

        async function removeChannel(encodedUrl) {
            const url = decodeURIComponent(encodedUrl);
            try {
                const res = await fetch(API_BASE + '/api/channels/remove', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ url: url })
                });
                if (res.ok) {
                    const data = await res.json();
                    renderChannels(data.channels || []);
                    showToast('Channel removed');
                }
            } catch (err) {
                showToast('Failed to remove', false);
            }
        }

        async function clearFeedCache() {
            try {
                const res = await fetch(API_BASE + '/api/cache/clear', { method: 'POST' });
                if (res.ok) {
                    showToast('⚡ In-memory feed cache cleared!');
                }
            } catch (err) {
                showToast('Failed to clear cache', false);
            }
        }

        async function resetDefaults() {
            if (!confirm('Are you sure you want to restore default settings and channels?')) {
                return;
            }
            try {
                const res = await fetch(API_BASE + '/api/reset', { method: 'POST' });
                if (res.ok) {
                    const data = await res.json();
                    loadSettings();
                    showToast('Restored default settings!');
                }
            } catch (err) {
                showToast('Failed to reset', false);
            }
        }

        // Initialize on load
        window.addEventListener('DOMContentLoaded', loadSettings);
    </script>
</body>
</html>
        """.trimIndent()
    }
}
