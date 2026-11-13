// ==ArenaAI==
// @id            auto_play_code
// @name          AutoPlayCode
// @version       1.7.0
// @type          extension
// @description   Envia respostas em fila, fecha enquetes e clica em botões de confirmação. Proteção contra cliques falsos, perda de fila e suporte a hot-reload.
// @author        Comunidade Arena AI
// @minAppVersion 2.0.0
// @match         *://arena.ai/*
// @compatible    true
// ==/ArenaAI==

(function (ArenaAI) {
    'use strict';

    var CFG = {
        MIN_INTERVAL_MS: 3000,
        DELAY_AFTER_COMPLETE: 800,
        MAX_SENDS_SESSION: 200,
    };

    var STORAGE_KEY = 'auto_play_code_v1';
    var queue = [];
    var paused = false;
    var lastSentAt = 0;
    var sentInSession = 0;
    var lockUntil = 0;
    var panel = null;
    var isUnloaded = false;
    var pendingTimers = [];
    var cleanupDragFn = null;

    function safeTimeout(fn, delay) {
        if (isUnloaded) return null;
        var id = setTimeout(function () {
            pendingTimers = pendingTimers.filter(function (t) { return t !== id; });
            if (!isUnloaded) fn();
        }, delay);
        pendingTimers.push(id);
        return id;
    }

    function clearAllTimers() {
        for (var i = 0; i < pendingTimers.length; i++) {
            clearTimeout(pendingTimers[i]);
        }
        pendingTimers = [];
    }

    // Helper para evitar Unhandled Promise Rejection
    function safeNotify(title, msg) {
        if (ArenaAI.notify) {
            try {
                var p = ArenaAI.notify(title, msg);
                if (p && typeof p.catch === 'function') p.catch(function(){});
            } catch(e) {}
        }
    }

    function load() {
        try {
            var raw = ArenaAI.storage.get(STORAGE_KEY);
            if (raw) {
                var data = JSON.parse(raw);
                queue = Array.isArray(data.queue) ? data.queue : [];
                paused = !!data.paused;
            }
        } catch (e) { queue = []; }
    }

    function save() {
        ArenaAI.storage.set(STORAGE_KEY, JSON.stringify({ queue: queue, paused: paused }));
    }

    function findTextarea() {
        var candidates = document.querySelectorAll('textarea[placeholder], textarea, [contenteditable="true"]');
        for (var i = 0; i < candidates.length; i++) {
            if (!candidates[i].closest('#autoqueue-panel')) return candidates[i];
        }
        return null;
    }

    function findSendButton() {
        // 1º Prioridade: Botão de enviar específico do Arena
        var specificBtn = document.querySelector('button[data-perf-target="send"]');
        if (specificBtn && !specificBtn.disabled) {
            return specificBtn;
        }

        // 2º Fallback: Varredura procurando send/enviar
        var btns = document.querySelectorAll('button');
        for (var i = 0; i < btns.length; i++) {
            var b = btns[i];
            if (b.closest('#autoqueue-panel')) continue;

            var label = ((b.getAttribute('aria-label') || '') + ' ' + (b.title || '') + ' ' + getCleanText(b)).toLowerCase();

            // Ignora botões de "Stop" ou "Parar" para não cancelar a geração
            if (label.indexOf('stop') !== -1 || label.indexOf('parar') !== -1) continue;

            if (label.indexOf('send') !== -1 || label.indexOf('enviar') !== -1) {
                if (!b.disabled) return b;
            }
        }

        // 3º Fallback: Form ou Irmão
        var ta = findTextarea();
        if (ta) {
            var form = ta.closest('form');
            if (form) {
                var submit = form.querySelector('button[type="submit"], button:last-of-type');
                if (submit && !submit.disabled) return submit;
            }
            var sib = ta.parentElement && ta.parentElement.querySelector('button');
            if (sib && !sib.disabled) return sib;
        }
        return null;
    }

    function setText(ta, text) {
        if (ta.tagName === 'TEXTAREA' || ta.tagName === 'INPUT') {
            var proto = ta.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
            var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
            setter.call(ta, text);
            ta.dispatchEvent(new Event('input', { bubbles: true }));
            ta.dispatchEvent(new Event('change', { bubbles: true }));
        } else {
            ta.focus();
            ta.innerText = text;
            ta.dispatchEvent(new InputEvent('input', { bubbles: true, data: text }));
        }
    }

    function getCleanText(el) {
        if (!el) return '';
        if (ArenaAI.dom && typeof ArenaAI.dom.cleanText === 'function') {
            return ArenaAI.dom.cleanText(el).toLowerCase();
        }
        try {
            var clone = el.cloneNode(true);
            var ignored = clone.querySelectorAll('svg, style, script');
            for (var i = 0; i < ignored.length; i++) {
                if (ignored[i].parentNode) ignored[i].parentNode.removeChild(ignored[i]);
            }
            return (clone.textContent || clone.innerText || '').replace(/[\s\u00A0]+/g, ' ').trim().toLowerCase();
        } catch (e) {
            return (el.innerText || el.textContent || '').replace(/[\s\u00A0]+/g, ' ').trim().toLowerCase();
        }
    }

    function simulateRealClick(btn) {
        if (!btn) return false;
        if (ArenaAI.dom && typeof ArenaAI.dom.click === 'function') {
            return ArenaAI.dom.click(btn);
        }

        try { btn.scrollIntoView({ block: 'center', inline: 'center', behavior: 'instant' }); } catch (e) {}
        try { btn.focus({ preventScroll: true }); } catch (e) { try { btn.focus(); } catch (e2) {} }

        var rect = btn.getBoundingClientRect ? btn.getBoundingClientRect() : { left: 0, top: 0, width: 0, height: 0 };
        var cx = Math.round(rect.left + (rect.width / 2));
        var cy = Math.round(rect.top + (rect.height / 2));

        var mouseOpts = {
            bubbles: true, cancelable: true, composed: true, view: window,
            clientX: cx, clientY: cy, screenX: cx, screenY: cy, button: 0, buttons: 1
        };

        function sendPointer(type, buttonsVal) {
            if (typeof PointerEvent !== 'function') return;
            try {
                btn.dispatchEvent(new PointerEvent(type, Object.assign({}, mouseOpts, {
                    buttons: buttonsVal, pointerId: 1, width: 1, height: 1,
                    pressure: buttonsVal ? 0.5 : 0, pointerType: 'touch', isPrimary: true
                })));
            } catch (e) {}
        }

        function sendTouch(type) {
            try {
                if (typeof Touch === 'function' && typeof TouchEvent === 'function') {
                    var t = new Touch({
                        identifier: Date.now() % 100000, target: btn,
                                      clientX: cx, clientY: cy, pageX: cx + window.scrollX, pageY: cy + window.scrollY,
                                      screenX: cx, screenY: cy, radiusX: 2.5, radiusY: 2.5, force: type === 'touchstart' ? 1 : 0
                    });
                    var active = type === 'touchstart' ? [t] : [];
                    btn.dispatchEvent(new TouchEvent(type, {
                        bubbles: true, cancelable: true, composed: true, view: window,
                        touches: active, targetTouches: active, changedTouches: [t]
                    }));
                    return;
                }
            } catch (e) {}
            try { btn.dispatchEvent(new Event(type, { bubbles: true, cancelable: true })); } catch (e2) {}
        }

        sendPointer('pointerdown', 1);
        sendTouch('touchstart');
        try { btn.dispatchEvent(new MouseEvent('mousedown', mouseOpts)); } catch (e) {}
        sendPointer('pointerup', 0);
        sendTouch('touchend');
        try { btn.dispatchEvent(new MouseEvent('mouseup', Object.assign({}, mouseOpts, { buttons: 0 }))); } catch (e) {}
        try { btn.dispatchEvent(new MouseEvent('click', Object.assign({}, mouseOpts, { buttons: 0 }))); } catch (e) {}
        try { if (typeof btn.click === 'function') btn.click(); } catch (e) {}
        sendPointer('pointercancel', 0);
        return true;
    }

    function clickActionButtons() {
        var targetTexts = ['sim', 'yes', 'continue', 'run', 'approve', 'allow', 'continuar', 'aprovar'];

        // Restrito APENAS a elementos clicáveis reais (button ou role=button)
        var elements = document.querySelectorAll('button, [role="button"]');

        for (var i = 0; i < elements.length; i++) {
            var el = elements[i];
            if (el.closest('#autoqueue-panel')) continue;

            // Pega o texto e limpa tags como SVG
            var text = getCleanText(el);
            if (!text || targetTexts.indexOf(text) === -1) continue;

            // Verifica estado do botão
            if (el.disabled || el.getAttribute('disabled') !== null || el.getAttribute('aria-disabled') === 'true') continue;

            // Verifica visibilidade
            var rect = el.getBoundingClientRect();
            var style = window.getComputedStyle(el);
            if (rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none') {
                simulateRealClick(el);
                console.log('[AutoPlayCode] Botão clicado:', text);
                return true;
            }
        }
        return false;
    }

    function tryClickActionsThenSend(attempt) {
        if (isUnloaded || paused || queue.length === 0) return; // Segurança contra polling fantasma e reload

        attempt = attempt || 1;
        if (clickActionButtons()) {
            safeTimeout(trySendNext, 1000);
        } else if (attempt < 10) {
            safeTimeout(function () {
                if (!isUnloaded && !paused && queue.length > 0) tryClickActionsThenSend(attempt + 1);
            }, 800);
        } else {
            console.log('[AutoPlayCode] Nenhum botão encontrado. Enviando direto.');
            trySendNext();
        }
    }

    function trySendNext() {
        if (isUnloaded || paused || queue.length === 0) return;
        if (Date.now() < lockUntil) return;
        if (Date.now() - lastSentAt < CFG.MIN_INTERVAL_MS) return;

        if (sentInSession >= CFG.MAX_SENDS_SESSION) {
            paused = true; save(); updatePanel();
            safeNotify('Fila Automática', 'Limite atingido.');
            return;
        }

        // ATENÇÃO: Não usamos shift() ainda. Apenas lemos o primeiro item.
        var next = queue[0];

        var ta = findTextarea();
        if (!ta) {
            safeNotify('Fila', 'Campo de texto não encontrado.');
            return;
        }

        lockUntil = Date.now() + 3000;
        setText(ta, next);
        ta.focus();

        safeTimeout(function () {
            var btn = findSendButton();
            if (btn && !btn.disabled) {
                simulateRealClick(btn);
            } else {
                ta.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
            }

            // Só removemos da fila se o campo de texto realmente esvaziou (indício de que o React aceitou o envio)
            safeTimeout(function() {
                var currentText = (ta.value || ta.innerText || '').trim();
                if (currentText === '') {
                    queue.shift(); // Remove de verdade
                    lastSentAt = Date.now();
                    sentInSession++;
                    save();
                    updatePanel();
                    console.log('[AutoPlayCode] Enviado com sucesso:', next.slice(0, 60));
                } else {
                    console.warn('[AutoPlayCode] O envio pode ter falhado. Item mantido na fila.');
                }
            }, 500); // Espera meio segundo para o React limpar o campo

        }, 150);
    }

    function forceSendNow(idx) {
        if (isUnloaded || idx < 0 || idx >= queue.length) return;
        var text = queue[idx];

        queue.splice(idx, 1);
        save();
        updatePanel();

        var ta = findTextarea();
        if (!ta) {
            safeNotify('Fila', 'Campo não encontrado. Item devolvido.');
            queue.push(text);
            save(); updatePanel();
            return;
        }

        lockUntil = Date.now() + 3000;
        lastSentAt = Date.now();

        setText(ta, text);
        ta.focus();

        safeTimeout(function () {
            var btn = findSendButton();
            if (btn && !btn.disabled) {
                simulateRealClick(btn);
            } else {
                ta.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
            }
            sentInSession++;
            updatePanel();
            console.log('[AutoPlayCode] Envio forçado (Start):', text.slice(0, 60));
            safeNotify('Fila', 'Start forçado enviado!');
        }, 150);
    }

    function escapeHtml(text) {
        return String(text).replace(/</g, '&lt;').replace(/"/g, '&quot;');
    }

    function buildPanel() {
        var existing = document.getElementById('autoqueue-panel');
        if (existing && existing.parentNode) {
            existing.parentNode.removeChild(existing);
        }
        panel = null;
        panel = document.createElement('div');
        panel.id = 'autoqueue-panel';
        panel.style.cssText =
        'position:fixed;top:80px;right:16px;z-index:99999;width:300px;' +
        'background:#0f172a;color:#e2e8f0;border:1px solid #38BDF8;' +
        'border-radius:12px;padding:12px;font-family:sans-serif;font-size:13px;' +
        'box-shadow:0 8px 24px rgba(0,0,0,.5);';

        panel.innerHTML =
        '<div id="aq-header" style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;cursor:move;touch-action:none;">' +
        '<strong>📬 AutoPlayCode</strong>' +
        '<div style="display:flex;gap:4px;">' +
        '<button id="aq-pause" style="background:#334155;color:#e2e8f0;border:none;border-radius:4px;padding:2px 8px;cursor:pointer;font-size:11px;">Pausar</button>' +
        '<button id="aq-toggle" style="background:none;border:none;color:#94a3b8;cursor:pointer;font-size:16px;">▾</button>' +
        '</div>' +
        '</div>' +
        '<div id="aq-body">' +
        '<div id="aq-list" style="max-height:180px;overflow:auto;margin-bottom:8px;"></div>' +
        '<textarea id="aq-input" placeholder="Digite uma resposta e pressione Enter..." rows="2" ' +
        'style="width:100%;box-sizing:border-box;background:#020617;color:#e2e8f0;' +
        'border:1px solid #334155;border-radius:6px;padding:6px;font-size:12px;resize:vertical;"></textarea>' +
        '<div style="display:flex;gap:6px;margin-top:6px;">' +
        '<button id="aq-add" style="flex:1;background:#38BDF8;color:#031E2F;border:none;' +
        'border-radius:6px;padding:6px;cursor:pointer;font-weight:bold;">+ Adicionar</button>' +
        '<button id="aq-clear" style="background:#334155;color:#e2e8f0;border:none;' +
        'border-radius:6px;padding:6px;cursor:pointer;">Limpar</button>' +
        '</div>' +
        '<div id="aq-status" style="margin-top:6px;font-size:11px;color:#94a3b8;"></div>' +
        '</div>';

        document.body.appendChild(panel);

        var header = document.getElementById('aq-header');
        var isDragging = false;
        var offsetX = 0, offsetY = 0;

        function startDrag(e) {
            if (e.target.tagName === 'BUTTON') return;
            isDragging = true;
            var rect = panel.getBoundingClientRect();
            panel.style.bottom = 'auto';
            panel.style.right = 'auto';
            panel.style.left = rect.left + 'px';
            panel.style.top = rect.top + 'px';

            var clientX = e.touches ? e.touches[0].clientX : e.clientX;
            var clientY = e.touches ? e.touches[0].clientY : e.clientY;

            offsetX = clientX - rect.left;
            offsetY = clientY - rect.top;

            document.addEventListener('mousemove', doDrag);
            document.addEventListener('touchmove', doDrag, { passive: false });
            document.addEventListener('mouseup', stopDrag);
            document.addEventListener('touchend', stopDrag);
        }

        function doDrag(e) {
            if (!isDragging) return;
            if (e.cancelable) e.preventDefault();

            var clientX = e.touches ? e.touches[0].clientX : e.clientX;
            var clientY = e.touches ? e.touches[0].clientY : e.clientY;

            var newLeft = clientX - offsetX;
            var newTop = clientY - offsetY;

            // Trava de limites da tela
            var maxLeft = window.innerWidth - panel.offsetWidth;
            var maxTop = window.innerHeight - panel.offsetHeight;

            newLeft = Math.max(0, Math.min(newLeft, maxLeft));
            newTop = Math.max(0, Math.min(newTop, maxTop));

            panel.style.left = newLeft + 'px';
            panel.style.top = newTop + 'px';
        }

        function stopDrag() {
            isDragging = false;
            document.removeEventListener('mousemove', doDrag);
            document.removeEventListener('touchmove', doDrag);
            document.removeEventListener('mouseup', stopDrag);
            document.removeEventListener('touchend', stopDrag);
        }

        header.addEventListener('mousedown', startDrag);
        header.addEventListener('touchstart', startDrag, { passive: false });
        cleanupDragFn = function () {
            stopDrag();
            header.removeEventListener('mousedown', startDrag);
            header.removeEventListener('touchstart', startDrag);
        };

        var body = document.getElementById('aq-body');
        var toggle = document.getElementById('aq-toggle');
        toggle.onclick = function () {
            body.style.display = body.style.display === 'none' ? 'block' : 'none';
            toggle.innerText = body.style.display === 'none' ? '▸' : '▾';
        };

        document.getElementById('aq-add').onclick = addItem;
        document.getElementById('aq-clear').onclick = function () {
            if (confirm('Limpar toda a fila?')) { queue = []; save(); updatePanel(); }
        };
        document.getElementById('aq-input').addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); addItem(); }
        });
        document.getElementById('aq-pause').onclick = function () {
            paused = !paused; save(); updatePanel();
            safeNotify('Fila', paused ? 'Pausada.' : 'Retomada.');
        };

        updatePanel();
    }

    function addItem() {
        var input = document.getElementById('aq-input');
        var text = (input.value || '').trim();
        if (!text) return;
        queue.push(text);
        input.value = '';
        save(); updatePanel();
    }

    function removeItem(idx) {
        queue.splice(idx, 1);
        save(); updatePanel();
    }

    function updatePanel() {
        var list = document.getElementById('aq-list');
        var status = document.getElementById('aq-status');
        var pauseBtn = document.getElementById('aq-pause');
        if (!list) return;

        if (queue.length === 0) {
            list.innerHTML = '<div style="color:#64748b;font-style:italic;padding:4px;">Fila vazia</div>';
        } else {
            list.innerHTML = queue.map(function (item, i) {
                var safe = escapeHtml(item);
                return '<div style="display:flex;gap:4px;margin-bottom:4px;background:#1e293b;padding:4px 6px;border-radius:4px;align-items:center;">' +
                '<span style="color:#38BDF8;flex-shrink:0;">' + (i + 1) + '.</span>' +
                '<span style="flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" title="' + safe + '">' + safe + '</span>' +
                '<button data-idx="' + i + '" class="aq-send-now" style="background:none;border:none;color:#38BDF8;cursor:pointer;font-size:16px;padding:0 4px;" title="Enviar Agora">⬆️</button>' +
                '<button data-idx="' + i + '" class="aq-del" style="background:none;border:none;color:#f87171;cursor:pointer;font-size:16px;padding:0 4px;" title="Excluir">✕</button>' +
                '</div>';
            }).join('');

            list.querySelectorAll('.aq-del').forEach(function (btn) {
                btn.onclick = function () { removeItem(parseInt(btn.getAttribute('data-idx'), 10)); };
            });

            list.querySelectorAll('.aq-send-now').forEach(function (btn) {
                btn.onclick = function () { forceSendNow(parseInt(btn.getAttribute('data-idx'), 10)); };
            });
        }

        if (status) {
            status.innerText = 'Na fila: ' + queue.length + '  •  Enviadas: ' + sentInSession + (paused ? '  •  ⏸ PAUSADA' : '');
        }
        if (pauseBtn) {
            pauseBtn.innerText = paused ? 'Retomar' : 'Pausar';
            pauseBtn.style.background = paused ? '#f59e0b' : '#334155';
        }
    }

    function cleanupExtension() {
        isUnloaded = true;
        clearAllTimers();
        if (typeof cleanupDragFn === 'function') {
            try { cleanupDragFn(); } catch (e) {}
            cleanupDragFn = null;
        }
        var existing = panel || document.getElementById('autoqueue-panel');
        if (existing && existing.parentNode) {
            existing.parentNode.removeChild(existing);
        }
        panel = null;
    }

    ArenaAI.registerExtension({
        id: 'auto_play_code',
        onReady: function () {
            isUnloaded = false;
            load();
            buildPanel();
            // Notificação única na inicialização
            safeNotify('AutoPlayCode', 'Carregada. ' + queue.length + ' item(ns) na fila.');

            ArenaAI.onResponseComplete(function () {
                if (isUnloaded) return;
                console.log('[AutoPlayCode] Resposta finalizada. Fila:', queue.length, 'paused:', paused);

                if (!paused && queue.length > 0) {
                    safeTimeout(function () {
                        tryClickActionsThenSend(1);
                    }, CFG.DELAY_AFTER_COMPLETE);
                }
            });

            ArenaAI.onPromptSubmit(function (prompt) {
                if (isUnloaded) return;
                console.log('[AutoPlayCode] onPromptSubmit:', String(prompt).slice(0, 40));
            });
        },
        onUnload: function () {
            console.log('[AutoPlayCode] Hot-reload: descarregando instância anterior...');
            cleanupExtension();
        }
    });
})(window.ArenaAI);
