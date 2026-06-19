const API = '/api';
let auth = { token: null };
let framesCache = {};
let frameData = null;

async function api(path, opts = {}) {
    const h = { 'Content-Type': 'application/json' };
    if (auth.token) h['Authorization'] = 'Bearer ' + auth.token;
    const r = await fetch(API + path, { ...opts, headers: { ...h, ...opts.headers } });
    if (r.status === 204) return null;
    return r.json();
}

async function tryAutoLogin() {
    const params = new URLSearchParams();
    params.append('username', 'admin');
    params.append('password', 'admin');
    try {
        const r = await fetch(API + '/auth/login', { method: 'POST', body: params });
        const data = await r.json();
        if (data && data.token) {
            auth.token = data.token;
            showToast('Sesión iniciada');
            refreshAll();
            setInterval(refreshAll, 3000);
        }
    } catch (e) {
        document.getElementById('status-badge').textContent = 'SIN CONEXIÓN';
    }
}

document.addEventListener('DOMContentLoaded', function() {
    var boot = function() {
        if (window.EvidexIcons) EvidexIcons.setPlayButton(false);
        tryAutoLogin();
    };
    if (window.EvidexIcons && EvidexIcons.ready) {
        EvidexIcons.ready.then(boot);
    } else {
        boot();
    }
});

async function refreshAll() {
    try {
        const [players, recordings, activeRecs] = await Promise.all([
            api('/players'),
            api('/recordings'),
            api('/recordings/active')
        ]);
        renderPlayers(players || []);
        renderSavedRecordings(recordings || []);
        renderActiveRecordings(activeRecs || []);
        updateStats(players, recordings, activeRecs);
        document.getElementById('status-badge').textContent = 'CONECTADO';
        document.getElementById('status-badge').className = 'badge badge-recording';
    } catch (e) {
        document.getElementById('status-badge').textContent = 'ERROR';
        document.getElementById('status-badge').className = 'badge badge-idle';
    }
}

function updateStats(players, recordings, activeRecs) {
    document.getElementById('stat-total').textContent = (recordings || []).length;
    document.getElementById('stat-active').textContent = (activeRecs || []).length;
    document.getElementById('stat-players').textContent = (players || []).length;
}

function renderPlayers(players) {
    const el = document.getElementById('players-list');
    if (!players.length) { el.innerHTML = '<div class="empty">No hay jugadores online</div>'; return; }
    el.innerHTML = players.map(p => `
        <div class="list-item">
            <div class="info">
                <span class="name">${escapeHtml(p)}</span>
            </div>
        </div>
    `).join('');
}

function renderActiveRecordings(recs) {
    const el = document.getElementById('active-recordings');
    if (!recs.length) { el.innerHTML = '<div class="empty">Ninguna grabación activa</div>'; return; }
    el.innerHTML = recs.map(r => `
        <div class="list-item">
            <div class="info">
                <span class="name">${escapeHtml(r.caseName || r.targetPlayer)}</span>
                <span class="details">${escapeHtml(r.targetPlayer)} · ${r.frameCount || 0} frames</span>
            </div>
            <span class="badge badge-recording">GRABANDO</span>
        </div>
    `).join('');
}

function renderSavedRecordings(recordings) {
    const el = document.getElementById('saved-recordings');
    if (!recordings.length) { el.innerHTML = '<div class="empty">No hay evidencias guardadas</div>'; return; }
    el.innerHTML = recordings.map(r => `
        <div class="evidence-row">
            <div>
                <div class="evidence-player">${escapeHtml(r.targetPlayer || 'Desconocido')}</div>
                <div class="evidence-date">${r.caseName ? escapeHtml(r.caseName) + ' · ' : ''}${formatDate(r.createdAt)}</div>
                <div class="evidence-stats">${r.frameCount || 0} fotogramas · ${escapeHtml(r.world || '—')}</div>
            </div>
            <div style="display:flex;align-items:center;gap:10px">
                <div class="evidence-duration">${formatDuration(r.duration)}</div>
                <button class="mc-btn mc-btn-primary small" onclick="openPlayer('${r.id}')">VER VIDEO</button>
                <button class="mc-btn mc-btn-danger small icon-btn" onclick="deleteEvidence('${r.id}')" aria-label="Eliminar"><span class="icon-slot" data-icon="delete" data-size="14"></span></button>
            </div>
        </div>
    `).join('');
    if (window.EvidexIcons) EvidexIcons.mountAll(el);
}

function deleteEvidence(id) {
    if (!confirm('¿Eliminar esta evidencia?')) return;
    api('/recordings/' + id, { method: 'DELETE' }).then(refreshAll).catch(() => showToast('Error al eliminar'));
}

function escapeHtml(s) {
    if (!s) return '';
    return String(s).replace(/[&<>"']/g, function(m) {
        if (m === '&') return '&amp;'; if (m === '<') return '&lt;'; if (m === '>') return '&gt;';
        if (m === '"') return '&quot;'; return '&#39;';
    });
}

function formatDate(ts) {
    if (!ts) return '—';
    const d = new Date(ts);
    return d.toLocaleDateString('es-ES', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

function formatDuration(secs) {
    if (!secs || secs <= 0) return '—';
    const m = Math.floor(secs / 60);
    const s = Math.floor(secs % 60);
    return m + 'm ' + s.toString().padStart(2, '0') + 's';
}

function showToast(msg) {
    const t = document.getElementById('toast');
    t.textContent = msg;
    t.className = 'toast show';
    clearTimeout(t._to);
    t._to = setTimeout(() => t.className = 'toast', 2500);
}

// ─── PLAYER ────────────────────────────────────────────────────────

let player = {
    frames: [],
    index: 0,
    playing: false,
    speed: 1,
    startTime: 0,
    pausedAt: 0,
    animId: null,
    recordingId: null,
    recordingInfo: null
};

async function openPlayer(id) {
    const modal = document.getElementById('player-modal');
    modal.style.display = 'flex';

    player.recordingId = id;
    player.index = 0;
    player.playing = false;
    player.speed = 1;
    player.pausedAt = 0;
    if (window.EvidexIcons) EvidexIcons.setPlayButton(false);

    document.getElementById('viewer-info').textContent = 'Cargando fotogramas...';

    framesCache = {};

    // fetch recording info for display
    try {
        const recs = await api('/recordings');
        player.recordingInfo = (recs || []).find(r => r.id === id);
    } catch (e) { /* ignore */ }

    // load all frames in chunks
    frameData = [];
    let offset = 0;
    while (true) {
        const chunk = await loadFrameChunk(id, offset);
        if (!chunk || chunk.length === 0) break;
        frameData = frameData.concat(chunk);
        offset += chunk.length;
        if (chunk.length < 500) break;
    }

    const totalFrames = frameData.length;
    if (totalFrames === 0) {
        document.getElementById('viewer-info').textContent = 'No hay fotogramas disponibles';
        return;
    }

    player.frames = frameData;
    player.index = 0;
    const pname = player.recordingInfo ? escapeHtml(player.recordingInfo.targetPlayer) + ' · ' : '';
    document.getElementById('viewer-info').textContent =
        pname + totalFrames + ' fotogramas';

    // setup scrubber (index-based)
    const scrubber = document.getElementById('scrubber');
    scrubber.max = totalFrames - 1;
    scrubber.value = 0;
    scrubber.oninput = function() {
        player.index = parseInt(this.value);
        if (player.playing) {
            player.startTime = performance.now() - (player.frames[player.index]?.timestamp || 0) / player.speed;
        }
        drawFrame(player.frames[player.index]);
        updateTimeDisplay();
    };

    // draw first frame
    drawFrame(player.frames[0]);
    updateTimeDisplay();
}

async function loadFrameChunk(id, offset) {
    try {
        const data = await api(`/recordings/${id}/frames?offset=${offset}&limit=500`);
        if (data && data.frames) {
            if (!framesCache[id]) framesCache[id] = [];
            framesCache[id] = framesCache[id].concat(data.frames);
            return data.frames;
        }
    } catch(e) {}
    return null;
}

function closePlayer() {
    if (player.animId) cancelAnimationFrame(player.animId);
    player.animId = null;
    player.playing = false;
    player.frames = [];
    player.index = 0;
    document.getElementById('player-modal').style.display = 'none';
}

function playerToggle() {
    if (player.frames.length === 0) return;
    player.playing = !player.playing;
    if (window.EvidexIcons) EvidexIcons.setPlayButton(player.playing);
    if (player.playing) {
        if (player.index >= player.frames.length - 1) {
            player.index = 0;
        }
        player.startTime = performance.now() - (player.frames[player.index]?.timestamp || 0) / player.speed;
        playerLoop();
    } else {
        if (player.animId) cancelAnimationFrame(player.animId);
        player.animId = null;
    }
}

function playerReset() {
    player.playing = false;
    player.index = 0;
    if (player.animId) cancelAnimationFrame(player.animId);
    player.animId = null;
    if (window.EvidexIcons) EvidexIcons.setPlayButton(false);
    document.getElementById('scrubber').value = 0;
    if (player.frames.length > 0) drawFrame(player.frames[0]);
    updateTimeDisplay();
}

function playerSpeed(s) {
    player.speed = s;
    document.querySelectorAll('.speed-controls button').forEach(b => b.classList.remove('active'));
    const btns = document.querySelectorAll('.speed-controls button');
    const speeds = [0.5, 1, 2, 5];
    const idx = speeds.indexOf(s);
    if (idx >= 0 && btns[idx]) btns[idx].classList.add('active');
    if (player.playing) {
        player.startTime = performance.now() - (player.frames[player.index]?.timestamp || 0) / player.speed;
    }
}

function playerLoop() {
    if (!player.playing || player.frames.length === 0) return;
    const elapsed = (performance.now() - player.startTime) * player.speed; // ms
    let targetIdx = player.index;
    for (let i = player.index; i < player.frames.length; i++) {
        if (player.frames[i].timestamp <= elapsed) targetIdx = i;
        else break;
    }
    if (targetIdx >= player.frames.length - 1) {
        player.playing = false;
        if (window.EvidexIcons) EvidexIcons.setPlayButton(false);
        targetIdx = player.frames.length - 1;
        document.getElementById('scrubber').value = player.frames.length - 1;
        drawFrame(player.frames[targetIdx]);
        updateTimeDisplay();
        return;
    }
    if (targetIdx !== player.index) {
        player.index = targetIdx;
        document.getElementById('scrubber').value = targetIdx;
        drawFrame(player.frames[targetIdx]);
        updateTimeDisplay();
    }
    player.animId = requestAnimationFrame(playerLoop);
}

function updateTimeDisplay() {
    if (player.frames.length === 0) return;
    const current = player.frames[player.index]?.timestamp || 0;
    const total = player.frames[player.frames.length - 1]?.timestamp || 0;
    document.getElementById('current-time').textContent = fmtTime(current);
    document.getElementById('total-time').textContent = fmtTime(total);
    document.getElementById('frame-info').textContent =
        (player.index + 1) + ' / ' + player.frames.length;
}

function fmtTime(ms) {
    const totalSec = Math.floor(ms / 1000);
    const m = Math.floor(totalSec / 60);
    const s = totalSec % 60;
    const tenths = Math.floor((ms % 1000) / 100);
    return m + ':' + s.toString().padStart(2, '0') + '.' + tenths;
}

// ─── 2D CANVAS RENDERER ────────────────────────────────────────────

function drawFrame(frame) {
    const canvas = document.getElementById('replay-canvas');
    if (!canvas || !frame) return;
    const rect = canvas.parentElement.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    canvas.width = rect.width * dpr;
    canvas.height = rect.height * dpr;
    canvas.style.width = rect.width + 'px';
    canvas.style.height = rect.height + 'px';
    const ctx = canvas.getContext('2d');
    ctx.scale(dpr, dpr);
    const w = rect.width, h = rect.height;

    // clear
    ctx.fillStyle = '#0a0d0a';
    ctx.fillRect(0, 0, w, h);

    // compute bounds from all frames
    const frames = player.frames;
    if (frames.length === 0) return;

    let minX = Infinity, maxX = -Infinity, minZ = Infinity, maxZ = -Infinity;
    for (const f of frames) {
        if (f.x < minX) minX = f.x;
        if (f.x > maxX) maxX = f.x;
        if (f.z < minZ) minZ = f.z;
        if (f.z > maxZ) maxZ = f.z;
    }

    const rangeX = maxX - minX || 1;
    const rangeZ = maxZ - minZ || 1;
    const padding = 40;
    const scale = Math.min((w - padding * 2) / rangeX, (h - padding * 2) / rangeZ);
    const cx = (minX + maxX) / 2;
    const cz = (minZ + maxZ) / 2;

    function toScreen(x, z) {
        return {
            sx: w / 2 + (x - cx) * scale,
            sy: h / 2 + (z - cz) * scale
        };
    }

    // grid
    ctx.strokeStyle = '#1a2a1a';
    ctx.lineWidth = 1;
    const gridStep = Math.pow(10, Math.floor(Math.log10(Math.max(rangeX, rangeZ) / 10)));
    for (let gx = Math.floor(minX / gridStep) * gridStep; gx <= maxX; gx += gridStep) {
        const p = toScreen(gx, 0);
        ctx.beginPath();
        ctx.moveTo(p.sx, 0); ctx.lineTo(p.sx, h);
        ctx.stroke();
    }
    for (let gz = Math.floor(minZ / gridStep) * gridStep; gz <= maxZ; gz += gridStep) {
        const p = toScreen(0, gz);
        ctx.beginPath();
        ctx.moveTo(0, p.sy); ctx.lineTo(w, p.sy);
        ctx.stroke();
    }

    // trail path
    ctx.strokeStyle = '#3d8b3d';
    ctx.lineWidth = 2;
    ctx.globalAlpha = 0.4;
    ctx.beginPath();
    for (let i = 0; i <= player.index; i++) {
        const p = toScreen(frames[i].x, frames[i].z);
        if (i === 0) ctx.moveTo(p.sx, p.sy);
        else ctx.lineTo(p.sx, p.sy);
    }
    ctx.stroke();

    // future trail
    ctx.strokeStyle = '#2a5a2a';
    ctx.lineWidth = 1.5;
    ctx.globalAlpha = 0.25;
    ctx.beginPath();
    for (let i = player.index; i < frames.length; i++) {
        const p = toScreen(frames[i].x, frames[i].z);
        if (i === player.index) ctx.moveTo(p.sx, p.sy);
        else ctx.lineTo(p.sx, p.sy);
    }
    ctx.stroke();

    ctx.globalAlpha = 1;

    // current position dot
    const pos = toScreen(frame.x, frame.z);
    ctx.fillStyle = '#76FF03';
    ctx.shadowColor = '#76FF03';
    ctx.shadowBlur = 12;
    ctx.beginPath();
    ctx.arc(pos.sx, pos.sy, 7, 0, Math.PI * 2);
    ctx.fill();
    ctx.shadowBlur = 0;

    // direction indicator
    if (frame.yaw !== undefined) {
        const len = 18;
        const rad = -frame.yaw * Math.PI / 180;
        const dx = Math.sin(rad) * len;
        const dz = Math.cos(rad) * len;
        ctx.strokeStyle = '#fff';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(pos.sx, pos.sy);
        ctx.lineTo(pos.sx + dx, pos.sy + dz);
        ctx.stroke();
    }

    // center coordinate label
    ctx.fillStyle = '#667a66';
    ctx.font = '12px monospace';
    ctx.textAlign = 'center';
    ctx.fillText('X: ' + Math.round(cx) + ' Z: ' + Math.round(cz), w / 2, h - 8);

    // update side panel
    document.getElementById('player-pos').textContent =
        'X: ' + (frame.x).toFixed(2) + ' Y: ' + (frame.y).toFixed(2) + ' Z: ' + (frame.z).toFixed(2) +
        ' · Yaw: ' + Math.round(frame.yaw) + '° Pitch: ' + Math.round(frame.pitch) + '°';

    document.getElementById('player-flags').textContent =
        (frame.flags && frame.flags.length > 0) ? frame.flags.join(', ') : '—';

    document.getElementById('player-equip').textContent =
        (frame.equipment && frame.equipment.length > 0) ? frame.equipment.join(', ') : '—';
}
