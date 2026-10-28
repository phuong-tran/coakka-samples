#include "stream_lane_camera_web_ui.h"

std::string_view coakka_v2_camera_web_ui_html() noexcept {
  static constexpr std::string_view kHtml = R"HTML(<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Pi Camera</title>
  <style>
    :root { color-scheme: dark; font-family: Inter, ui-sans-serif, system-ui, sans-serif; }
    * { box-sizing: border-box; letter-spacing: 0; }
    html, body { margin: 0; width: 100%; height: 100%; background: #101214; color: #f5f7f8; }
    body { overflow: hidden; }
    .shell { position: relative; width: 100%; height: 100%; background: #08090a; }
    .camera { width: 100%; height: 100%; object-fit: contain; display: block; }
    .camera.off { visibility: hidden; }
    .bar { position: absolute; left: 0; right: 0; display: flex; align-items: center; gap: 12px; padding: 12px 16px; background: rgba(12, 14, 16, .88); backdrop-filter: blur(10px); }
    .top { top: 0; min-height: 56px; border-bottom: 1px solid #30353a; }
    .bottom { bottom: 0; min-height: 68px; justify-content: center; border-top: 1px solid #30353a; flex-wrap: wrap; }
    .brand { font-size: 16px; font-weight: 700; white-space: nowrap; }
    .status { display: flex; align-items: center; gap: 8px; min-width: 0; color: #bec5ca; font-size: 13px; }
    .status strong { color: #f5f7f8; font-weight: 600; }
    .dot { width: 9px; height: 9px; border-radius: 50%; background: #777f85; flex: 0 0 auto; }
    .dot.online { background: #38c172; box-shadow: 0 0 0 3px rgba(56, 193, 114, .18); }
    .metrics { margin-left: auto; display: flex; gap: 16px; color: #aab2b8; font-size: 12px; white-space: nowrap; }
    button, select { height: 40px; border: 1px solid #41484e; border-radius: 6px; background: #20252a; color: #f7f8f9; padding: 0 14px; font: inherit; font-size: 14px; font-weight: 650; }
    button { cursor: pointer; }
    button:hover { background: #2a3036; }
    button:focus-visible { outline: 2px solid #61a8ff; outline-offset: 2px; }
    button:disabled { opacity: .45; cursor: default; }
    .primary { background: #1769aa; border-color: #2785d1; }
    .primary:hover { background: #1c78bf; }
    .recording { background: #a82b35; border-color: #d84a55; }
    .danger { color: #ffb2b8; }
    .audio-option { height: 40px; display: inline-flex; align-items: center; gap: 7px; padding: 0 10px; color: #d9dee1; font-size: 14px; white-space: nowrap; }
    .audio-option input { width: 17px; height: 17px; accent-color: #2785d1; }
    .empty { position: absolute; inset: 56px 0 68px; display: grid; place-items: center; color: #8c969d; font-size: 14px; pointer-events: none; }
    .empty.hidden { display: none; }
    .path { position: absolute; right: 16px; bottom: 78px; max-width: min(640px, calc(100% - 32px)); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #d4d9dc; background: rgba(13, 15, 17, .84); border: 1px solid #343a40; border-radius: 4px; padding: 7px 9px; font-size: 12px; }
    .path:empty { display: none; }
    @media (max-width: 720px) { .metrics { display: none; } .bar { padding: 10px; } .bottom { gap: 8px; } button, select { flex: 1 1 auto; padding: 0 10px; } }
  </style>
</head>
<body>
  <main class="shell">
    <img id="camera" class="camera" alt="Live camera">
    <div id="empty" class="empty">Livestream stopped</div>
    <header class="bar top">
      <div class="brand">Pi Camera</div>
      <div class="status"><span id="dot" class="dot"></span><strong id="connection">Disconnected</strong><span id="transport"></span></div>
      <div class="metrics"><span id="frames">0 frames</span><span id="drops">0 drops</span><span id="recorded">0 recorded</span></div>
    </header>
    <div id="recordingPath" class="path"></div>
    <footer class="bar bottom">
      <button id="connect" class="primary" title="Connect control WebSocket">Connect</button>
      <select id="profile" title="Camera resolution">
        <option value="640x480x30">640×480</option>
        <option value="800x600x30">800×600</option>
        <option value="1280x720x30" selected>1280×720</option>
        <option value="1920x1080x30">1920×1080</option>
      </select>
      <button id="live" title="Start or stop the browser livestream">▶ Live</button>
      <label class="audio-option" title="Include camera microphone in the next recording"><input id="audio" type="checkbox">Audio</label>
      <button id="record" title="Start or stop recording">● Record</button>
      <button id="stopSession" class="danger" title="Cancel the Stream Lane session">■ Stop session</button>
    </footer>
  </main>
  <script>
    const ui = Object.fromEntries(['camera','empty','dot','connection','transport','frames','drops','recorded','recordingPath','connect','profile','live','audio','record','stopSession'].map(id => [id, document.getElementById(id)]));
    let socket = null;
    let live = false;
    let recording = false;
    const requestId = () => crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`;
    const send = (command, fields = {}) => {
      if (!socket || socket.readyState !== WebSocket.OPEN) return;
      socket.send(JSON.stringify({ id: requestId(), command, ...fields }));
    };
    const setLiveImage = enabled => {
      live = enabled;
      ui.live.textContent = enabled ? '■ Stop live' : '▶ Live';
      ui.camera.classList.toggle('off', !enabled);
      ui.empty.classList.toggle('hidden', enabled);
      if (enabled) ui.camera.src = `/stream.mjpeg?t=${Date.now()}`;
      else ui.camera.removeAttribute('src');
    };
    const render = status => {
      const connected = status.control === 'connected';
      ui.dot.classList.toggle('online', connected);
      ui.connection.textContent = connected ? 'Connected' : 'Disconnected';
      ui.transport.textContent = status.transport ? `· ${status.transport}` : '';
      ui.frames.textContent = `${status.received_frames || 0} frames`;
      ui.drops.textContent = `${(status.source_drops || 0) + (status.display_drops || 0)} drops`;
      ui.recorded.textContent = `${status.recording_frames || 0} recorded`;
      ui.recordingPath.textContent = status.recording_path || '';
      recording = status.recording_state === 'recording' || status.recording_state === 'starting';
      ui.record.textContent = recording ? '■ Stop record' : status.recording_state === 'finalizing' ? '… Finalizing' : '● Record';
      ui.record.classList.toggle('recording', recording);
      ui.record.disabled = !connected || status.recording_state === 'finalizing';
      ui.profile.disabled = !connected || recording || status.profile_state === 'switching';
      ui.audio.disabled = !connected || recording || !status.audio_available;
      const activeProfile = `${status.profile_width || 1280}x${status.profile_height || 720}x${status.profile_fps || 30}`;
      if (status.profile_state !== 'switching' && [...ui.profile.options].some(option => option.value === activeProfile)) ui.profile.value = activeProfile;
      ui.live.disabled = !connected;
      ui.stopSession.disabled = !connected || status.transport === 'terminal';
      if (typeof status.livestream_enabled === 'boolean' && status.livestream_enabled !== live) setLiveImage(status.livestream_enabled);
    };
    const disconnect = () => {
      if (socket) socket.close(1000, 'user disconnect');
    };
    const connect = () => {
      if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) return;
      socket = new WebSocket(`ws://${location.host}/ws/control`);
      ui.connection.textContent = 'Connecting';
      socket.onopen = () => { ui.connect.textContent = 'Disconnect'; send('status.get'); };
      socket.onmessage = event => { try { render(JSON.parse(event.data)); } catch (_) {} };
      socket.onclose = () => { render({control:'disconnected', transport:'offline', livestream_enabled:false}); ui.connect.textContent = 'Connect'; socket = null; };
      socket.onerror = () => socket && socket.close();
    };
    ui.connect.addEventListener('click', () => socket && socket.readyState === WebSocket.OPEN ? disconnect() : connect());
    ui.profile.addEventListener('change', () => {
      const [width, height, fps] = ui.profile.value.split('x').map(Number);
      send('resolution.set', {width, height, fps});
    });
    ui.live.addEventListener('click', () => send(live ? 'livestream.stop' : 'livestream.start'));
    ui.record.addEventListener('click', () => recording ? send('recording.stop') : send('recording.start', {audio: ui.audio.checked}));
    ui.stopSession.addEventListener('click', () => send('session.disconnect'));
    setLiveImage(false);
    connect();
  </script>
</body>
</html>)HTML";
  return kHtml;
}
