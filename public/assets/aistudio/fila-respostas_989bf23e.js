// ==ArenaAI==
// @id            fila-respostas
// @name          Fila de Respostas Automáticas
// @version       1.3.1
// @type          extension
// @description   Envia respostas em fila, fecha enquetes e clica em botões de confirmação (Continue/Run) automaticamente. Painel arrastável via touch e mouse.
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

    var STORAGE_KEY = 'fila_respostas_v1';
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
        return document.querySelector('textarea[placeholder]')
            || document.querySelector('textarea')
            || document.querySelector('[contenteditable="true"]');
    }

    function findSendButton() {
        var btns = document.querySelectorAll('button');
        for (var i = 0; i < btns.length; i++) {
            var b = btns[i];
            var label = ((b.getAttribute('aria-label') || '') + ' ' +
                         (b.title || '') + ' ' +
                         (b.innerText || '')).toLowerCase();
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

    // === FUNÇÃO DE CLIQUE REFORÇADA ===
    function clickActionButtons() {
        var targetTexts = ['sim', 'yes', 'continue', 'run', 'approve', 'allow', 'continuar', 'aprovar'];
        // Busca em botões, spans e divs
        var elements = document.querySelectorAll('button, span, div');

        for (var i = 0; i < elements.length; i++) {
            var el = elements[i];

            // Ignora elementos que têm filhos (queremos o nó folha)
            if (el.children.length > 0) continue;

            var text = (el.innerText || el.textContent || '').trim().toLowerCase();

            if (targetTexts.indexOf(text) !== -1) {
                var btn = el.closest('button') || el; // Pega o botão pai

                // Verifica se está desabilitado
                if (btn.disabled || btn.getAttribute('disabled') !== null) {
                    continue;
                }

                // Verifica se está visível
                var rect = btn.getBoundingClientRect();
                if (rect.width > 0 && rect.height > 0) {
                    // Simula clique completo (crucial para React)
                    var opts = { bubbles: true, cancelable: true, view: window };
                    btn.dispatchEvent(new MouseEvent('mousedown', opts));
                    btn.dispatchEvent(new MouseEvent('mouseup', opts));
                    btn.dispatchEvent(new MouseEvent('click', opts));

                    // Fallback nativo
                    btn.click();

                    console.log('[Fila] Botão clicado:', text);
                    ArenaAI.notify('Fila Debug', 'Botão "' + text + '" clicado!');
                    return true;
                }
            }
        }
        return false;
    }

    // === FUNÇÃO DE RETENTATIVA MAIS TEIMOSA ===
    function tryClickActionsThenSend(attempt) {
        attempt = attempt || 1;
        if (clickActionButtons()) {
            // Se clicou, espera 1s e envia
            setTimeout(trySendNext, 1000);
        } else if (attempt < 10) { // Aumentado para 10 tentativas
            setTimeout(function() { tryClickActionsThenSend(attempt + 1); }, 1000);
        } else {
            console.log('[Fila] Desisti de achar botão. Enviando direto.');
            ArenaAI.notify('Fila Debug', 'Desisti de achar botão.');
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
                btn.click();
            } else {
                ta.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
            }
            lastSentAt = Date.now();
            sentInSession++;
            updatePanel();
            console.log('[Fila] Enviado:', next.slice(0, 60));
        }, 150);
    }

    function escapeHtml(text) {
        return String(text).replace(/</g, '&lt;').replace(/"/g, '&quot;');
    }

    function buildPanel() {
        if (panel) return;
        panel = document.createElement('div');
        panel.id = 'autoqueue-panel';
        // Posição inicial: topo direito, longe do botão de enviar
        panel.style.cssText =
            'position:fixed;top:80px;right:16px;z-index:99999;width:300px;' +
            'background:#0f172a;color:#e2e8f0;border:1px solid #38BDF8;' +
            'border-radius:12px;padding:12px;font-family:sans-serif;font-size:13px;' +
            'box-shadow:0 8px 24px rgba(0,0,0,.5);';

        panel.innerHTML =
            // Adicionado id="aq-header" e cursor:move para indicar que pode arrastar
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

        // ===== LÓGICA DE ARRASTAR (DRAG & DROP) =====
        var header = document.getElementById('aq-header');
        var isDragging = false;
        var offsetX = 0, offsetY = 0;

        function startDrag(e) {
            if (e.target.tagName === 'BUTTON') return; // Ignora cliques nos botões
            isDragging = true;
            var rect = panel.getBoundingClientRect();

            // Desativa o alinhamento do CSS e usa left/top absoluto
            panel.style.bottom = 'auto';
            panel.style.right = 'auto';
            panel.style.left = rect.left + 'px';
            panel.style.top = rect.top + 'px';

            var clientX = e.touches ? e.touches[0].clientX : e.clientX;
            var clientY = e.touches ? e.touches[0].clientY : e.clientY;

            offsetX = clientX - rect.left;
            offsetY = clientY - rect.top;

            document.addEventListener('mousemove', doDrag);
            document.addEventListener('touchmove', doDrag, {passive: false});
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
        header.addEventListener('touchstart', startDrag, {passive: false});
        // ============================================

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
                return '<div style="display:flex;gap:4px;margin-bottom:4px;background:#1e293b;padding:4px 6px;border-radius:4px;">' +
                    '<span style="color:#38BDF8;flex-shrink:0;">' + (i + 1) + '.</span>' +
                    '<span style="flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" title="' + safe + '">' + safe + '</span>' +
                    '<button data-idx="' + i + '" class="aq-del" style="background:none;border:none;color:#f87171;cursor:pointer;">✕</button>' +
                '</div>';
            }).join('');
            list.querySelectorAll('.aq-del').forEach(function (btn) {
                btn.onclick = function () {
                    removeItem(parseInt(btn.getAttribute('data-idx'), 10));
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
                ArenaAI.notify('Fila Debug', 'Resposta finalizada! Fila: ' + queue.length); // ADICIONADO

                if (!paused && queue.length > 0) {
                    setTimeout(function() {
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
