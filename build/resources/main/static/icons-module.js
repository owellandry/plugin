import React from 'https://cdn.jsdelivr.net/npm/react@19.2.4/+esm';
import { createRoot } from 'https://cdn.jsdelivr.net/npm/react-dom@19.2.4/client/+esm';
import {
    AiOutlineTeam,
    AiOutlineReload,
    AiFillAudio,
    AiOutlineFolder,
    AiOutlineVideoCamera,
    AiOutlineClose,
    AiOutlinePlayCircle,
    AiOutlinePauseCircle,
    AiOutlineDelete,
    AiOutlineSafety
} from 'https://cdn.jsdelivr.net/npm/react-icons@5.6.0/ai/+esm';

const ICONS = {
    team: AiOutlineTeam,
    reload: AiOutlineReload,
    recording: AiFillAudio,
    folder: AiOutlineFolder,
    video: AiOutlineVideoCamera,
    close: AiOutlineClose,
    play: AiOutlinePlayCircle,
    pause: AiOutlinePauseCircle,
    delete: AiOutlineDelete,
    shield: AiOutlineSafety
};

const roots = new WeakMap();

function renderIcon(el, name, options) {
    if (!el) return;
    const Component = ICONS[name];
    if (!Component) return;

    const size = options.size || el.dataset.size || 18;
    const className = [options.className, el.dataset.iconClass].filter(Boolean).join(' ');

    let root = roots.get(el);
    if (!root) {
        root = createRoot(el);
        roots.set(el, root);
    }

    root.render(React.createElement(Component, {
        size: size,
        className: className || undefined,
        color: options.color || el.dataset.color || undefined
    }));
}

function mountAll(container) {
    const scope = container || document;
    scope.querySelectorAll('[data-icon]').forEach(function(el) {
        renderIcon(el, el.dataset.icon, {
            size: el.dataset.size,
            className: el.dataset.iconClass,
            color: el.dataset.color
        });
    });
}

function setPlayButton(playing) {
    const btn = document.getElementById('play-btn');
    if (!btn) return;
    renderIcon(btn, playing ? 'pause' : 'play', { size: 16 });
    btn.setAttribute('aria-label', playing ? 'Pausar' : 'Reproducir');
}

const ready = Promise.resolve().then(function() {
    mountAll();
    setPlayButton(false);
}).catch(function(err) {
    console.error('[EvidexIcons] Error cargando iconos:', err);
});

window.EvidexIcons = {
    ready: ready,
    mountAll: mountAll,
    setPlayButton: setPlayButton,
    renderIcon: renderIcon
};