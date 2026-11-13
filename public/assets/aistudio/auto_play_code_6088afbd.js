// ==ArenaAI==
// @id            auto_play_code
// @name          AutoPlayCode
// @version       1.5.0
// @type          extension
// @description   Envia respostas em fila, fecha enquetes e clica em botões de confirmação. Permite forçar envio imediato pela seta.
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
        var btns = document.querySelectorAll('button');
        for (var i = 0; i < btns.length; i++) {
            var b = btns[i];
            if (b.closest('#autoqueue-panel')) continue;
            var label = ((b.getAttribute('aria-label') || '') + ' ' +
                         (b.title || '') + ' ' +
                         getCleanText(b)).toLowerCase();
            if (label.indexOf('send') !== -1 || label.indexOf('enviar') !== -1) return b;
        }
        var ta = findTextarea();
        if (ta) {
            var form = ta.closest('form');
            if (form) {
                var submit = form.querySelector('button[type="submit"], button:last-of-type');
                if (submit) return submit;
            }
            var sib = ta.parentElement && ta.parentElement.querySelector('button');
            if (sib) return sib;
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
            return (clone.textContent || clone.innerText || '')
                .replace(/[\s\u00A0]+/g, ' ')
                .trim()
                .toLowerCase();
        } catch (e) {
            return (el.innerText || el.textContent || '')
                .replace(/[\s\u00A0]+/g, ' ')
                .trim()
                .toLowerCase();
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
        var elements = document.querySelectorAll('button, [role="button"], span, div');

        for (var i = 0; i < elements.length; i++) {
            var el = elements[i];
            if (el.closest('#autoqueue-panel')) continue;
            if (el.tagName === 'DIV' && el.querySelectorAll('button, div').length > 0) continue;

            var text = getCleanText(el);
            if (!text || targetTexts.indexOf(text) === -1) continue;

            var btn = el.closest('button, [role="button"]') || el;
            if (btn.disabled || btn.getAttribute('disabled') !== null || btn.getAttribute('aria-disabled') === 'true') continue;

            var rect = btn.getBoundingClientRect();
            var style = window.getComputedStyle(btn);
            if (rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none') {
                simulateRealClick(btn);
                console.log('[Fila] Botão clicado:', text);
                ArenaAI.notify('Fila Debug', 'Botão "' + text + '" clicado!');
                return true;
            }
        }
        return false;
    }

    function tryClickActionsThenSend(attempt) {
        attempt = attempt || 1;
        if (clickActionButtons()) {
            setTimeout(trySendNext, 1000);
        } else if (attempt < 10) {
            setTimeout(function () { tryClickActionsThenSend(attempt + 1); }, 800);
        } else {
            console.log('[Fila] Nenhum botão encontrado. Enviando direto.');
            trySendNext();
        }
    }

    function trySendNext() {
        if (paused) return;
        if (queue.length === 0) { updatePanel(); return; }
        if (Date.now() < lockUntil) return;
        if (Date.now() - lastSentAt < CFG.MIN_INTERVAL_MS) return;
        if (sentInSession >= CFG.MAX_SENDS_SESSION) {
            paused = true; save(); updatePanel();
            ArenaAI.notify('Fila Automática', 'Limite atingido.');
            return;
        }

        var next = queue.shift();
        save(); updatePanel();

        var ta = findTextarea();
        if (!ta) {
            ArenaAI.notify('Fila', 'Campo de texto não encontrado. Devolvendo à fila.');
            queue.unshift(next); save(); updatePanel();
            return;
        }

        lockUntil = Date.now() + 2000;
        setText(ta, next);
        ta.focus();

        setTimeout(function () {
            var btn = findSendButton();
            if (btn && !btn.disabled) {
                simulateRealClick(btn);
            } else {
                ta.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
            }
            lastSentAt = Date.now();
            sentInSession++;
            updatePanel();
            console.log('[Fila] Enviado:', next.slice(0, 60));
        }, 150);
    }

    // === NOVA FUNÇÃO: Enviar item específico imediatamente (Seta ⬆️) ===
    function forceSendNow(idx) {
        if (idx < 0 || idx >= queue.length) return;
        var text = queue[idx];
        
        // Remove da fila para o fluxo automático não enviar de novo
        queue.splice(idx, 1);
        save();
        updatePanel();

        var ta = findTextarea();
        if (!ta) {
            ArenaAI.notify('Fila', 'Campo não encontrado. Item devolvido à fila.');
            queue.push(text); // Devolve para o fim da fila
            save(); updatePanel();
            return;
        }

        // Trava o fluxo automático por 3 segundos para darmos o Start
        lockUntil = Date.now() + 3000;
        lastSentAt = Date.now();

        setText(ta, text);
        ta.focus();

        setTimeout(function () {
            var btn = findSendButton();
            if (btn && !btn.disabled) {
                simulateRealClick(btn);
            } else {
                ta.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
            }
            sentInSession++;
            updatePanel();
            console.log('[Fila] Envio forçado (Start):', text.slice(0, 60));
            ArenaAI.notify('Fila', 'Start forçado enviado!');
        }, 150);
    }

    function escapeHtml(text) {
        return String(text).replace(/</g, '&lt;').replace(/"/g, '&quot;');
    }

    function buildPanel() {
        if (panel) return;
        panel = document.createElement('div');
        panel.id = 'autoqueue-panel';
        panel.style.cssText =
            'position:fixed;top:80px;right:16px;z-index:99999;width:300px;' +
            'background:#0f172a;color:#e2e8f0;border:1px solid #38BDF8;' +
            'border-radius:12px;padding:12px;font-family:sans-serif;font-size:13px;' +
            'box-shadow:0 8px 24px rgba(0,0,0,.5);';

        panel.innerHTML =
            '<div id="aq-header" style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;cursor:move;touch-action:none;">' +
                '<strong>📬 Fila Automática</strong>' +
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

            panel.style.left = (clientX - offsetX) + 'px';
            panel.style.top = (clientY - offsetY) + 'px';
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
            ArenaAI.notify('Fila', paused ? 'Pausada.' : 'Retomada.');
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
                    // NOVO BOTÃO DE SETA
                    '<button data-idx="' + i + '" class="aq-send-now" style="background:none;border:none;color:#38BDF8;cursor:pointer;font-size:16px;padding:0 4px;" title="Enviar Agora">⬆️</button>' +
                    // BOTÃO X
                    '<button data-idx="' + i + '" class="aq-del" style="background:none;border:none;color:#f87171;cursor:pointer;font-size:16px;padding:0 4px;" title="Excluir">✕</button>' +
                '</div>';
            }).join('');
            
            // Eventos dos botões de excluir
            list.querySelectorAll('.aq-del').forEach(function (btn) {
                btn.onclick = function () {
                    removeItem(parseInt(btn.getAttribute('data-idx'), 10));
                };
            });

            // Eventos do novo botão de enviar agora (Seta)
            list.querySelectorAll('.aq-send-now').forEach(function (btn) {
                btn.onclick = function () {
                    var idx = parseInt(btn.getAttribute('data-idx'), 10);
                    forceSendNow(idx);
                };
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

    ArenaAI.registerExtension({
        id: 'fila-respostas',
        onReady: function () {
            load();
            buildPanel();
            ArenaAI.notify('Fila Automática', 'Carregada. ' + queue.length + ' item(ns) na fila.');

            ArenaAI.onResponseComplete(function () {
                console.log('[Fila] onResponseComplete. Fila:', queue.length, 'paused:', paused);
                ArenaAI.notify('Fila Debug', 'Resposta finalizada! Fila: ' + queue.length);

                if (!paused && queue.length > 0) {
                    setTimeout(function () {
                        tryClickActionsThenSend(1);
                    }, CFG.DELAY_AFTER_COMPLETE);
                }
            });

            ArenaAI.onPromptSubmit(function (prompt) {
                console.log('[Fila] onPromptSubmit:', String(prompt).slice(0, 40));
            });
        }
    });
})(window.ArenaAI);
