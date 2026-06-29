const API = '/api';
let auth = { token: null };
let activeRecordings = [];
let onlineAdmins = [];
let replayTarget = null;

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
    var boot = function() { tryAutoLogin(); };
    if (window.EvidexIcons && EvidexIcons.ready) {
        EvidexIcons.ready.then(boot);
    } else {
        boot();
    }
});

async function refreshAll() {
    try {
        const [players, recordings, activeRecs, admins] = await Promise.all([
            api('/players'),
            api('/recordings'),
            api('/recordings/active'),
            api('/admins')
        ]);
        activeRecordings = activeRecs || [];
        onlineAdmins = admins || [];
        renderPlayers(players || []);
        renderSavedRecordings(recordings || []);
        renderActiveRecordings(activeRecordings);
        updateStats(players, recordings, activeRecordings);
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

function isRecording(playerName) {
    return activeRecordings.some(function(r) { return r.targetPlayer === playerName; });
}

function renderPlayers(players) {
    const el = document.getElementById('players-list');
    if (!players.length) {
        el.innerHTML = '<div class="empty">No hay jugadores online</div>';
        return;
    }
    el.innerHTML = players.map(function(p) {
        const recording = isRecording(p);
        return `
        <div class="list-item">
            <div class="info">
                <span class="name">${escapeHtml(p)}</span>
            </div>
            <div class="actions">
                ${recording
                    ? `<button class="mc-btn mc-btn-danger small" onclick="stopRecording('${escapeHtml(p)}')">PARAR</button>`
                    : `<button class="mc-btn mc-btn-primary small" onclick="startRecording('${escapeHtml(p)}')">GRABAR</button>`
                }
            </div>
        </div>`;
    }).join('');
}

function renderActiveRecordings(recs) {
    const el = document.getElementById('active-recordings');
    if (!recs.length) {
        el.innerHTML = '<div class="empty">Ninguna grabación activa</div>';
        return;
    }
    el.innerHTML = recs.map(function(r) {
        return `
        <div class="list-item">
            <div class="info">
                <span class="name">${escapeHtml(r.targetPlayer)}</span>
                <span class="details">${r.frameCount || 0} fotogramas</span>
            </div>
            <span class="badge badge-recording">GRABANDO</span>
        </div>`;
    }).join('');
}

function renderSavedRecordings(recordings) {
    const el = document.getElementById('saved-recordings');
    if (!recordings.length) {
        el.innerHTML = '<div class="empty">No hay evidencias guardadas</div>';
        return;
    }
    el.innerHTML = recordings.map(function(r) {
        return `
        <div class="evidence-row">
            <div>
                <div class="evidence-player">${escapeHtml(r.targetPlayer || 'Desconocido')}</div>
                <div class="evidence-date">${formatDate(r.createdAt)}</div>
                <div class="evidence-stats">${r.frameCount || 0} fotogramas · ${escapeHtml(r.world || '—')}</div>
            </div>
            <div class="evidence-actions">
                <div class="evidence-duration">${formatDuration(r.duration)}</div>
                <button class="mc-btn mc-btn-primary small" onclick="openReplayModal('${r.id}')">REPRODUCIR</button>
                <button class="mc-btn mc-btn-danger small icon-btn" onclick="deleteEvidence('${r.id}')" aria-label="Eliminar">
                    <span class="icon-slot" data-icon="delete" data-size="14"></span>
                </button>
            </div>
        </div>`;
    }).join('');
    if (window.EvidexIcons) EvidexIcons.mountAll(el);
}

async function startRecording(player) {
    try {
        const res = await api('/record/start', { method: 'POST', body: JSON.stringify({ player: player }) });
        if (res && res.success) {
            showToast('Grabación iniciada: ' + player);
            refreshAll();
        } else {
            showToast(res && res.error ? res.error : 'Error al grabar');
        }
    } catch (e) {
        showToast('Error al iniciar grabación');
    }
}

async function stopRecording(player) {
    try {
        const res = await api('/record/stop', { method: 'POST', body: JSON.stringify({ player: player }) });
        if (res && res.success) {
            showToast('Grabación detenida: ' + player);
            refreshAll();
        } else {
            showToast(res && res.error ? res.error : 'Error al parar');
        }
    } catch (e) {
        showToast('Error al detener grabación');
    }
}

function deleteEvidence(id) {
    if (!confirm('¿Eliminar esta evidencia?')) return;
    api('/recordings/' + id, { method: 'DELETE' }).then(refreshAll).catch(function() {
        showToast('Error al eliminar');
    });
}

function openReplayModal(id) {
    replayTarget = id;
    const modal = document.getElementById('replay-modal');
    const select = document.getElementById('admin-select');
    const info = document.getElementById('replay-info');
    const hint = document.getElementById('replay-hint');

    info.textContent = 'Evidencia #' + id;
    select.innerHTML = '';

    if (!onlineAdmins.length) {
        select.innerHTML = '<option value="">— Sin admins online —</option>';
        hint.textContent = 'Ningún admin con permiso evidex.admin está conectado al servidor.';
        document.getElementById('play-replay-btn').disabled = true;
    } else {
        onlineAdmins.forEach(function(name) {
            const opt = document.createElement('option');
            opt.value = name;
            opt.textContent = name;
            select.appendChild(opt);
        });
        hint.textContent = 'El admin seleccionado verá el replay dentro del juego.';
        document.getElementById('play-replay-btn').disabled = false;
    }

    document.getElementById('stop-replay-btn').style.display = onlineAdmins.length ? 'inline-block' : 'none';
    modal.style.display = 'flex';
    if (window.EvidexIcons) EvidexIcons.mountAll(modal);
}

function closeReplayModal() {
    document.getElementById('replay-modal').style.display = 'none';
    replayTarget = null;
}

async function startReplay() {
    if (!replayTarget) return;
    const viewer = document.getElementById('admin-select').value;
    if (!viewer) {
        showToast('Selecciona un admin online');
        return;
    }
    const btn = document.getElementById('play-replay-btn');
    btn.disabled = true;
    try {
        const res = await api('/replay/start-id', {
            method: 'POST',
            body: JSON.stringify({ id: replayTarget, viewer: viewer })
        });
        if (res && res.success) {
            showToast(res.message || 'Replay iniciado en el juego');
            closeReplayModal();
        } else {
            showToast(res && res.error ? res.error : 'No se pudo iniciar el replay');
        }
    } catch (e) {
        showToast('Error de conexión');
    } finally {
        btn.disabled = false;
    }
}

async function stopReplay() {
    const viewer = document.getElementById('admin-select').value;
    if (!viewer) {
        showToast('Selecciona un admin');
        return;
    }
    try {
        const res = await api('/replay/stop', {
            method: 'POST',
            body: JSON.stringify({ viewer: viewer })
        });
        if (res && res.success) {
            showToast('Replay detenido para ' + viewer);
            closeReplayModal();
        } else {
            showToast(res && res.error ? res.error : 'No se pudo detener');
        }
    } catch (e) {
        showToast('Error de conexión');
    }
}

function escapeHtml(s) {
    if (!s) return '';
    return String(s).replace(/[&<>"']/g, function(m) {
        if (m === '&') return '&amp;';
        if (m === '<') return '&lt;';
        if (m === '>') return '&gt;';
        if (m === '"') return '&quot;';
        return '&#39;';
    });
}

function formatDate(ts) {
    if (!ts) return '—';
    const d = new Date(ts);
    return d.toLocaleDateString('es-ES', {
        day: '2-digit', month: 'short', year: 'numeric',
        hour: '2-digit', minute: '2-digit'
    });
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
    t._to = setTimeout(function() { t.className = 'toast'; }, 2500);
}