(function () {
  'use strict';
  var ID = 'modelagent';
  if (window.__modelAgentLoaded) return;
  window.__modelAgentLoaded = true;

  var state = {
    apiKey: '',
    models: [],
    selected: { build: '', dialogue: '', chat: '' },
    agent: 'build',
    messages: { build: [], dialogue: [], chat: [] },
    busy: false,
    aborter: null,
    lastPlan: '',
    lastArenaPreview: '',
    webSearch: false
  };

  var AGENTS = {
    build: {
      label: 'Build',
      role: 'Arquiteto técnico e gerente de execução. Você planeja tarefas de engenharia de software, decompõe em etapas pequenas, identifica arquivos e riscos, e prepara instruções precisas para outro modelo executar dentro do Arena AI.',
      system: 'Você é o agente gerente do ModelAgent. O modelo da Arena AI será o engenheiro executor. Analise o objetivo, transforme-o em um plano técnico acionável e produza uma mensagem final para o executor. Não afirme que alterou arquivos. Não invente estrutura do projeto. Quando não houver contexto suficiente, declare hipóteses e peça inspeção. Prefira mudanças pequenas, testáveis e reversíveis. A mensagem de handoff deve conter objetivo, contexto, escopo permitido, arquivos que podem ser tocados (se conhecidos), restrições, critérios de aceitação e validação. Não permita que o executor altere arquivos fora do escopo.',
      temperature: 0.25,
      maxTokens: 1800
    },
    dialogue: {
      label: 'Diálogo',
      role: 'Agente generalista para pesquisa, análise, planejamento, redação e tarefas pontuais.',
      system: 'Você é o agente Diálogo do ModelAgent. Responda com rigor, organize pesquisas e tarefas em etapas verificáveis, diferencie fatos de hipóteses e use ferramentas de pesquisa quando habilitadas. Seja completo sem desperdiçar tokens.',
      temperature: 0.45,
      maxTokens: 2200
    },
    chat: {
      label: 'Chat',
      role: 'Assistente para perguntas curtas e respostas diretas.',
      system: 'Você é o agente Chat do ModelAgent. Responda diretamente, com precisão e concisão. Não elabore planos longos sem necessidade.',
      temperature: 0.35,
      maxTokens: 700
    }
  };

  var host = document.createElement('div');
  host.id = 'modelagent-host';
  host.style.cssText = 'all:initial;position:relative;z-index:2147483000;';
  (document.documentElement || document.body).appendChild(host);
  var shadow = host.attachShadow({ mode: 'open' });

  function el(tag, attrs, children) {
    var node = document.createElement(tag);
    Object.keys(attrs || {}).forEach(function (k) {
      if (k === 'class') node.className = attrs[k];
      else if (k === 'text') node.textContent = attrs[k];
      else if (k === 'html') node.innerHTML = attrs[k];
      else if (k.slice(0, 2) === 'on') node.addEventListener(k.slice(2).toLowerCase(), attrs[k]);
      else node.setAttribute(k, attrs[k]);
    });
    (children || []).forEach(function (child) { node.appendChild(typeof child === 'string' ? document.createTextNode(child) : child); });
    return node;
  }

  var styleLink = document.createElement('link');
  styleLink.rel = 'stylesheet';
  styleLink.href = '';
  var css = ":host { all: initial; }\n#ma-root, #ma-root * { box-sizing: border-box; }\n#ma-root {\n  --ma-bg: #101216; --ma-panel: #191d24; --ma-panel2: #222832;\n  --ma-line: #303743; --ma-text: #edf1f7; --ma-muted: #9aa5b4;\n  --ma-accent: #8b7cff; --ma-accent2: #6d5ce7; --ma-green: #62d6a3;\n  --ma-yellow: #f0c674; --ma-red: #ff8585;\n  color: var(--ma-text); font: 13px/1.45 Inter, system-ui, sans-serif;\n}\n#ma-launch {\n  position: fixed; right: 18px; bottom: 92px; z-index: 2147483000;\n  width: 54px; height: 54px; border: 1px solid #a99fff66; border-radius: 18px;\n  background: linear-gradient(145deg,#8c7dff,#5545c9); color: white;\n  box-shadow: 0 8px 28px #0008; font-weight: 800; font-size: 14px; cursor: pointer;\n}\n#ma-panel {\n  position: fixed; z-index: 2147483000; right: 18px; bottom: 156px;\n  width: min(440px, calc(100vw - 28px)); height: min(720px, calc(100dvh - 190px));\n  display: none; flex-direction: column; overflow: hidden; border-radius: 18px;\n  border: 1px solid #ffffff20; background: var(--ma-bg); box-shadow: 0 18px 70px #000b;\n}\n#ma-panel.open { display: flex; }\n.ma-head { display:flex; align-items:center; justify-content:space-between; padding:14px 16px; border-bottom:1px solid var(--ma-line); background:#171a20; }\n.ma-brand { display:flex; align-items:center; gap:10px; font-weight:800; font-size:15px; letter-spacing:.2px; }\n.ma-mark { display:grid; place-items:center; width:30px;height:30px;border-radius:10px;background:#7666ed;color:white;font-weight:900; }\n.ma-sub { color:var(--ma-muted);font-size:10px;font-weight:500; }\n.ma-iconbtn,.ma-btn { border:1px solid var(--ma-line); background:var(--ma-panel2); color:var(--ma-text); border-radius:9px; padding:7px 10px; cursor:pointer; }\n.ma-iconbtn { font-size:18px; padding:3px 9px; }\n.ma-body { display:flex; flex-direction:column; min-height:0; flex:1; }\n.ma-tabs { display:grid;grid-template-columns:repeat(3,1fr);gap:6px;padding:10px;border-bottom:1px solid var(--ma-line); }\n.ma-tab { border:1px solid transparent;background:transparent;color:var(--ma-muted);border-radius:9px;padding:9px 4px;font-weight:700;cursor:pointer; }\n.ma-tab.active { color:white;background:#7666ed22;border-color:#7666ed88; }\n.ma-config { padding:10px 12px;display:flex;gap:7px;align-items:center;border-bottom:1px solid var(--ma-line); }\n.ma-config input { min-width:0;flex:1; }\n.ma-input,.ma-select,.ma-textarea { width:100%; border:1px solid var(--ma-line);border-radius:9px;background:#11151a;color:var(--ma-text);padding:10px;font:inherit;outline:none; }\n.ma-input:focus,.ma-select:focus,.ma-textarea:focus { border-color:var(--ma-accent); }\n.ma-section { padding:12px;display:flex;flex-direction:column;gap:9px;min-height:0;overflow:auto; }\n.ma-label { color:var(--ma-muted);font-size:10px;text-transform:uppercase;letter-spacing:.09em;font-weight:800; }\n.ma-modelbox { border:1px solid var(--ma-line);border-radius:11px;overflow:hidden;background:var(--ma-panel); }\n.ma-modeltop { display:flex;align-items:center;gap:8px;padding:10px;cursor:pointer; }\n.ma-modeltop select { flex:1;min-width:0; }\n.ma-modelrow { display:flex;align-items:center;gap:8px;padding:9px 10px;border-top:1px solid #ffffff0d;cursor:pointer; }\n.ma-modelrow:hover,.ma-modelrow.selected { background:#7666ed1b; }\n.ma-modelname { flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-weight:650; }\n.ma-free { color:#8de0b6;background:#173d30;border-radius:5px;padding:2px 5px;font-size:9px;font-weight:900; }\n.ma-paid { color:#c0b7ff;background:#2a2550;border-radius:5px;padding:2px 5px;font-size:9px;font-weight:800; }\n.ma-chevron { color:var(--ma-muted);font-size:15px; }\n.ma-details { padding:10px;border-top:1px solid var(--ma-line);display:grid;grid-template-columns:1fr 1fr;gap:8px;background:#12161c; }\n.ma-detail { min-width:0; }\n.ma-detail b { display:block;font-size:11px;overflow-wrap:anywhere; }\n.ma-detail span { color:var(--ma-muted);font-size:10px; }\n.ma-search { margin:0 10px 10px; width:calc(100% - 20px); }\n.ma-model-list { max-height:210px;overflow:auto;border-top:1px solid var(--ma-line); }\n.ma-actions { display:flex;gap:7px;flex-wrap:wrap; }\n.ma-btn.primary { background:var(--ma-accent2);border-color:var(--ma-accent);color:white;font-weight:750; }\n.ma-btn:disabled { opacity:.45;cursor:not-allowed; }\n.ma-status { color:var(--ma-muted);font-size:11px;min-height:16px; }\n.ma-status.error { color:var(--ma-red); }\n.ma-status.ok { color:var(--ma-green); }\n.ma-chat { display:flex;flex-direction:column;gap:9px;min-height:190px;flex:1; }\n.ma-messages { display:flex;flex-direction:column;gap:9px;overflow:auto;min-height:100px;flex:1;padding:2px; }\n.ma-msg { border:1px solid var(--ma-line);background:var(--ma-panel);border-radius:12px;padding:10px;white-space:pre-wrap;overflow-wrap:anywhere; }\n.ma-msg.user { border-color:#7666ed66;background:#292544; }\n.ma-msg .meta { color:var(--ma-muted);font-size:10px;margin-bottom:5px;font-weight:800; }\n.ma-textarea { resize:vertical;min-height:76px;max-height:180px; }\n.ma-row { display:flex;gap:7px;align-items:center; }\n.ma-row > * { min-width:0; }\n.ma-check { display:flex;align-items:center;gap:6px;color:var(--ma-muted);font-size:11px; }\n.ma-note { color:var(--ma-muted);font-size:10px;line-height:1.5; }\n.ma-divider { height:1px;background:var(--ma-line);margin:2px 0; }\n.ma-pill { border:1px solid var(--ma-line);border-radius:99px;padding:3px 7px;color:var(--ma-muted);font-size:10px; }\n.ma-empty { color:var(--ma-muted);text-align:center;padding:22px 10px; }\n@media(max-width:520px) {\n #ma-launch { right:12px;bottom:78px; }\n #ma-panel { right:8px;bottom:140px;width:calc(100vw - 16px);height:min(700px,calc(100dvh - 160px)); }\n}\n";
  // CSS file is loaded by the add-on manager through the SDK; this fallback keeps the UI usable
  // if a host build does not inject manifest styles.
  var style = document.createElement('style');
  style.textContent = css;
  shadow.appendChild(style);

  var root = el('div', { id: 'ma-root' });
  var launch = el('button', { id: 'ma-launch', title: 'Abrir ModelAgent', text: 'MA' });
  var panel = el('section', { id: 'ma-panel', role: 'dialog', 'aria-label': 'ModelAgent' });
  shadow.appendChild(root);
  root.appendChild(launch);
  root.appendChild(panel);

  function setStatus(message, kind) {
    var node = shadow.getElementById('ma-status');
    if (!node) return;
    node.textContent = message || '';
    node.className = 'ma-status' + (kind ? ' ' + kind : '');
  }

  function safeText(v) { return String(v == null ? '' : v); }
  function money(value) {
    var n = Number(value);
    if (!Number.isFinite(n)) return 'n/d';
    if (n === 0) return 'grátis';
    return '$' + n.toFixed(n < 0.001 ? 8 : 6).replace(/0+$/, '').replace(/\\.$/, '');
  }
  function isFree(m) {
    var p = m && m.pricing || {};
    return (String(m.id || '').endsWith(':free')) ||
      (Number(p.prompt) === 0 && Number(p.completion) === 0);
  }
  function modality(m, key) {
    var v = m && m.architecture && m.architecture[key];
    return Array.isArray(v) ? v.join(', ') : safeText(v || 'text');
  }
  function modelById(id) {
    return state.models.find(function (m) { return m.id === id; }) || null;
  }

  function apiFetch(path, options) {
    if (!state.apiKey) return Promise.reject(new Error('Informe sua chave OpenRouter.'));
    var headers = Object.assign({
      'Authorization': 'Bearer ' + state.apiKey,
      'Content-Type': 'application/json',
      'HTTP-Referer': 'https://arena.ai/agent',
      'X-OpenRouter-Title': 'ArenaAI ModelAgent'
    }, options && options.headers || {});
    return fetch('https://openrouter.ai/api/v1' + path, Object.assign({}, options || {}, { headers: headers }));
  }

  function loadModels() {
    setStatus('Carregando catálogo OpenRouter…');
    return apiFetch('/models', { method: 'GET' }).then(function (r) {
      if (!r.ok) return r.text().then(function (t) { throw new Error('OpenRouter ' + r.status + ': ' + t.slice(0, 240)); });
      return r.json();
    }).then(function (json) {
      state.models = (json.data || []).filter(function (m) { return m && m.id; });
      state.models.sort(function (a, b) {
        var f = Number(isFree(b)) - Number(isFree(a));
        return f || safeText(a.name || a.id).localeCompare(safeText(b.name || b.id));
      });
      ['build', 'dialogue', 'chat'].forEach(function (a) {
        if (!state.selected[a] || !modelById(state.selected[a])) {
          var candidate = state.models.find(function (m) { return isFree(m); }) || state.models[0];
          state.selected[a] = candidate ? candidate.id : '';
        }
      });
      render();
      setStatus(state.models.length + ' modelos carregados.', 'ok');
    }).catch(function (e) {
      setStatus(e.message || 'Falha ao carregar modelos.', 'error');
      throw e;
    });
  }

  function modelDetails(m) {
    if (!m) return '<div class="ma-note">Selecione um modelo para ver os detalhes.</div>';
    var p = m.pricing || {}, tp = m.top_provider || {};
    var input = modality(m, 'input_modalities'), output = modality(m, 'output_modalities');
    var tools = Array.isArray(m.supported_parameters) && m.supported_parameters.indexOf('tools') >= 0;
    var values = [
      ['ID', m.id], ['Contexto total', Number(m.context_length || 0).toLocaleString()],
      ['Saída máxima', Number(tp.max_completion_tokens || m.top_provider && m.top_provider.max_completion_tokens || 0).toLocaleString()],
      ['Entrada', input], ['Saída', output], ['Tools', tools ? 'Suportado' : 'Não listado'],
      ['Prompt / 1M tokens', money(Number(p.prompt || 0) * 1000000)],
      ['Completion / 1M tokens', money(Number(p.completion || 0) * 1000000)]
    ];
    return '<div class="ma-details">' + values.map(function (v) {
      return '<div class="ma-detail"><span>' + escapeHtml(v[0]) + '</span><b>' + escapeHtml(safeText(v[1])) + '</b></div>';
    }).join('') + '</div>';
  }
  function escapeHtml(s) {
    return safeText(s).replace(/[&<>"']/g, function (c) {
      return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c];
    });
  }

  function renderModelPicker() {
    var box = el('div', { class: 'ma-modelbox' });
    var current = modelById(state.selected[state.agent]);
    var currentLabel = current ? (current.name || current.id) : 'Nenhum modelo selecionado';
    box.appendChild(el('div', { class: 'ma-modeltop' }, [
      el('div', { class: 'ma-modelname', text: currentLabel }),
      el('span', { class: 'ma-pill', text: current && isFree(current) ? 'FREE' : 'MODELO' })
    ]));
    var search = el('input', { class: 'ma-input ma-search', type: 'search', placeholder: 'Filtrar por nome ou ID do modelo…' });
    box.appendChild(search);
    var list = el('div', { class: 'ma-model-list' });
    box.appendChild(list);
    function paint(filter) {
      list.replaceChildren();
      var q = safeText(filter).toLowerCase().trim();
      var filtered = state.models.filter(function (m) {
        return !q || safeText(m.id).toLowerCase().includes(q) || safeText(m.name).toLowerCase().includes(q);
      }).slice(0, 100);
      if (!filtered.length) {
        list.appendChild(el('div', { class: 'ma-empty', text: 'Nenhum modelo encontrado.' }));
        return;
      }
      filtered.forEach(function (m) {
        var item = el('div', { class: 'ma-modelitem' });
        var row = el('button', { class: 'ma-modelrow' + (m.id === state.selected[state.agent] ? ' selected' : '') });
        var label = el('div', { class: 'ma-modelname' });
        label.appendChild(el('b', { text: m.name || m.id }));
        label.appendChild(el('small', { text: m.id }));
        row.appendChild(label);
        row.appendChild(el('span', { class: isFree(m) ? 'ma-free' : 'ma-paid', text: isFree(m) ? 'FREE' : 'PAGO' }));
        row.addEventListener('click', function () {
          state.selected[state.agent] = m.id;
          render();
          setStatus('Modelo selecionado: ' + m.id, 'ok');
        });
        item.appendChild(row);
        var details = el('details', { class: 'ma-modeldetails' });
        details.appendChild(el('summary', { class: 'ma-chevron', text: '⌄' }));
        details.appendChild(el('div', { html: modelDetails(m) }));
        item.appendChild(details);
        list.appendChild(item);
      });
    }
    search.addEventListener('input', function () { paint(search.value); });
    paint('');
    return box;
  }

  function addMessage(role, content) {
    var list = state.messages[state.agent];
    list.push({ role: role, content: content });
    if (list.length > 40) list.splice(0, list.length - 40);
    renderMessages();
  }
  function renderMessages() {
    var list = shadow.getElementById('ma-messages');
    if (!list) return;
    list.replaceChildren();
    var messages = state.messages[state.agent];
    if (!messages.length) {
      list.appendChild(el('div', { class: 'ma-empty', text: 'Escolha um agente e envie sua primeira mensagem.' }));
      return;
    }
    messages.forEach(function (m) {
      var node = el('div', { class: 'ma-msg ' + (m.role === 'user' ? 'user' : '') });
      node.appendChild(el('div', { class: 'meta', text: m.role === 'user' ? 'VOCÊ' : 'MODELAGENT' }));
      node.appendChild(document.createTextNode(m.content));
      list.appendChild(node);
    });
    list.scrollTop = list.scrollHeight;
  }

  function streamCompletion(messages, onToken) {
    var model = state.selected[state.agent];
    if (!model) return Promise.reject(new Error('Selecione um modelo.'));
    var body = {
      model: model,
      messages: messages,
      stream: true,
      temperature: AGENTS[state.agent].temperature,
      max_tokens: AGENTS[state.agent].maxTokens,
      usage: { include: true }
    };
    if (state.webSearch && state.agent === 'dialogue') body.plugins = [{ id: 'web' }];
    state.aborter = new AbortController();
    return apiFetch('/chat/completions', {
      method: 'POST',
      signal: state.aborter.signal,
      body: JSON.stringify(body)
    }).then(function (r) {
      if (!r.ok) return r.text().then(function (t) { throw new Error('OpenRouter ' + r.status + ': ' + t.slice(0, 500)); });
      if (!r.body || !r.body.getReader) throw new Error('Streaming não suportado neste WebView.');
      var reader = r.body.getReader(), decoder = new TextDecoder('utf-8'), buffer = '';
      function read() {
        return reader.read().then(function (part) {
          if (part.done) return;
          buffer += decoder.decode(part.value, { stream: true });
          var lines = buffer.split(/\\r?\\n/);
          buffer = lines.pop() || '';
          lines.forEach(function (line) {
            if (line.indexOf('data:') !== 0) return;
            var data = line.slice(5).trim();
            if (!data || data === '[DONE]') return;
            try {
              var parsed = JSON.parse(data);
              var delta = parsed.choices && parsed.choices[0] && parsed.choices[0].delta;
              if (delta && typeof delta.content === 'string') onToken(delta.content);
            } catch (_) {}
          });
          return read();
        });
      }
      return read();
    });
  }

  function sendChat(text) {
    if (state.busy || !text.trim()) return;
    if (!state.apiKey) { setStatus('Configure sua chave OpenRouter primeiro.', 'error'); return; }
    state.busy = true;
    var input = text.trim();
    addMessage('user', input);
    var output = '';
    state.messages[state.agent].push({ role: 'assistant', content: '' });
    render();
    var history = state.messages[state.agent].slice(0, -1).map(function (m) { return { role: m.role, content: m.content }; });
    var messages = [{ role: 'system', content: AGENTS[state.agent].system }].concat(history);
    var bubbleIndex = state.messages[state.agent].length - 1;
    setStatus('Agente ' + AGENTS[state.agent].label + ' trabalhando…');
    streamCompletion(messages, function (token) {
      output += token;
      state.messages[state.agent][bubbleIndex].content = output;
      renderMessages();
    }).then(function () {
      state.lastPlan = state.agent === 'build' ? output : state.lastPlan;
      setStatus('Resposta concluída.', 'ok');
    }).catch(function (e) {
      if (e.name !== 'AbortError') setStatus(e.message || 'Erro na geração.', 'error');
      state.messages[state.agent][bubbleIndex].content = output || '[Geração interrompida ou sem conteúdo.]';
      renderMessages();
    }).finally(function () {
      state.busy = false; state.aborter = null; renderControls();
    });
    renderControls();
  }

  function visible(elm) {
    if (!elm || !elm.isConnected) return false;
    var r = elm.getBoundingClientRect(), s = getComputedStyle(elm);
    return r.width > 0 && r.height > 0 && s.display !== 'none' && s.visibility !== 'hidden';
  }
  function findComposer() {
    var selectors = [
      'textarea[placeholder*="Ask" i]', 'textarea[placeholder*="message" i]',
      'textarea[placeholder*="pergunt" i]', 'textarea',
      '[contenteditable="true"][role="textbox"]', '[contenteditable="true"]',
      '[role="textbox"]'
    ];
    for (var i = 0; i < selectors.length; i++) {
      var nodes = Array.from(document.querySelectorAll(selectors[i])).filter(visible);
      var valid = nodes.find(function (n) {
        if (host.contains(n)) return false;
        var aria = (n.getAttribute('aria-label') || '') + ' ' + (n.getAttribute('placeholder') || '');
        var rect = n.getBoundingClientRect();
        return rect.width > 180 && rect.height > 24 &&
          !/search|pesquisa|buscar/i.test(aria);
      });
      if (valid) return valid;
    }
    return null;
  }
  function insertIntoArena(text) {
    var composer = findComposer();
    if (!composer) throw new Error('Não encontrei o campo de mensagem da Arena AI. Abra uma conversa e tente novamente.');
    composer.focus();
    if (composer.isContentEditable || composer.getAttribute('contenteditable') === 'true') {
      composer.textContent = text;
      composer.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: text }));
    } else {
      var proto = composer.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
      var setter = Object.getOwnPropertyDescriptor(proto, 'value');
      if (setter && setter.set) setter.set.call(composer, text);
      else composer.value = text;
      composer.dispatchEvent(new Event('input', { bubbles: true }));
      composer.dispatchEvent(new Event('change', { bubbles: true }));
    }
    composer.focus();
    composer.scrollIntoView({ block: 'center', behavior: 'smooth' });
    return true;
  }

  function buildPanel() {
    panel.replaceChildren();
    var header = el('header', { class: 'ma-head' });
    var brand = el('div', { class: 'ma-brand' }, [
      el('div', { class: 'ma-mark', text: 'M' }),
      el('div', {}, [
        el('div', { text: 'ModelAgent' }),
        el('div', { class: 'ma-sub', text: 'OpenRouter · Arena AI bridge' })
      ])
    ]);
    header.appendChild(brand);
    header.appendChild(el('button', { class: 'ma-iconbtn', text: '×', title: 'Fechar', onclick: function () { panel.classList.remove('open'); } }));
    panel.appendChild(header);

    var tabs = el('nav', { class: 'ma-tabs' });
    ['build', 'dialogue', 'chat'].forEach(function (id) {
      tabs.appendChild(el('button', {
        class: 'ma-tab' + (state.agent === id ? ' active' : ''),
        text: AGENTS[id].label,
        onclick: function () { state.agent = id; render(); }
      }));
    });
    panel.appendChild(tabs);

    var config = el('div', { class: 'ma-config' });
    var key = el('input', { class: 'ma-input', type: 'password', placeholder: 'OpenRouter API Key (sk-or-...)', autocomplete: 'off' });
    key.value = state.apiKey;
    key.addEventListener('input', function () { state.apiKey = key.value.trim(); });
    config.appendChild(key);
    config.appendChild(el('button', { class: 'ma-btn', text: 'Conectar', onclick: function () { state.apiKey = key.value.trim(); loadModels().catch(function () {}); } }));
    panel.appendChild(config);

    var section = el('div', { class: 'ma-section' });
    section.appendChild(el('div', { class: 'ma-label', text: 'Modelo do agente ' + AGENTS[state.agent].label }));
    section.appendChild(renderModelPicker());
    if (state.agent === 'dialogue') {
      var check = el('label', { class: 'ma-check' });
      var cb = el('input', { type: 'checkbox' }); cb.checked = state.webSearch;
      cb.addEventListener('change', function () { state.webSearch = cb.checked; });
      check.appendChild(cb); check.appendChild(document.createTextNode('Habilitar pesquisa web (plugin OpenRouter)'));
      section.appendChild(check);
    }

    if (state.agent === 'build') {
      section.appendChild(el('div', { class: 'ma-note', text: 'O ModelAgent atua como gerente: cria um plano técnico e prepara o handoff. Você revisa e insere a instrução no chat da Arena AI; o envio continua manual.' }));
    }
    section.appendChild(el('div', { class: 'ma-divider' }));
    var chat = el('div', { class: 'ma-chat' });
    chat.appendChild(el('div', { id: 'ma-messages', class: 'ma-messages' }));
    var textarea = el('textarea', { id: 'ma-prompt', class: 'ma-textarea', placeholder: state.agent === 'build' ? 'Descreva o projeto, objetivo, restrições e resultado esperado…' : 'Escreva sua mensagem…' });
    chat.appendChild(textarea);
    var actions = el('div', { class: 'ma-actions' });
    var send = el('button', { id: 'ma-send', class: 'ma-btn primary', text: state.agent === 'build' ? 'Gerar plano' : 'Enviar', onclick: function () { sendChat(textarea.value); textarea.value = ''; } });
    actions.appendChild(send);
    actions.appendChild(el('button', { class: 'ma-btn', text: 'Parar', onclick: function () { if (state.aborter) state.aborter.abort(); } }));
    if (state.agent === 'build') {
      actions.appendChild(el('button', { class: 'ma-btn', text: 'Inserir na Arena', onclick: function () {
        var plan = state.lastPlan || (state.messages.build.length ? state.messages.build[state.messages.build.length - 1].content : '');
        if (!plan.trim()) { setStatus('Gere um plano antes de inserir.', 'error'); return; }
        try { insertIntoArena(plan); setStatus('Plano inserido no composer. Revise antes de enviar.', 'ok'); }
        catch (e) { setStatus(e.message, 'error'); }
      } }));
    }
    chat.appendChild(actions);
    section.appendChild(chat);
    section.appendChild(el('div', { id: 'ma-status', class: 'ma-status', text: state.models.length ? state.models.length + ' modelos disponíveis.' : 'Insira sua chave OpenRouter e conecte.' }));
    section.appendChild(el('div', { class: 'ma-note', text: 'A chave fica apenas na memória desta página. Não feche ou recarregue a Arena AI enquanto estiver usando a sessão. Nunca compartilhe sua chave.' }));
    panel.appendChild(section);
    renderMessages();
    renderControls();
  }

  function renderControls() {
    var send = shadow.getElementById('ma-send');
    if (send) send.disabled = state.busy;
  }
  function render() {
    buildPanel();
    if (state.models.length) setStatus(state.models.length + ' modelos carregados.', 'ok');
  }
  function onArenaResponse(preview) {
    state.lastArenaPreview = safeText(preview).slice(0, 1000);
    if (state.agent === 'build' && state.lastArenaPreview) {
      addMessage('system', 'Preview da resposta da Arena AI (limite atual de 1.000 caracteres):\\n' + state.lastArenaPreview);
    }
  }

  launch.addEventListener('click', function () { panel.classList.toggle('open'); });
  if (window.ArenaAI && typeof window.ArenaAI.onResponseComplete === 'function') {
    window.ArenaAI.onResponseComplete(onArenaResponse);
  }
  window.addEventListener('beforeunload', function () {
    if (state.aborter) state.aborter.abort();
    state.apiKey = '';
    try { delete window.__modelAgentLoaded; } catch (_) {}
  });

  buildPanel();
  if (window.ArenaAI && typeof window.ArenaAI.registerPlugin === 'function') {
    window.ArenaAI.registerPlugin({
      id: ID,
      onReady: function () { console.info('[ModelAgent] plugin ready', window.ArenaAI.version); },
      onUnload: function () {
        if (state.aborter) state.aborter.abort();
        state.apiKey = '';
        host.remove();
        try { delete window.__modelAgentLoaded; } catch (_) {}
      }
    });
  }
})();
