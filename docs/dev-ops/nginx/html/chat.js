(function () {
  'use strict';

  var cookieName = 'ai_agent_demo_user';
  var storageKey = 'ai_agent_api_config';

  function getUser() {
    var cookie = document.cookie.split(';').map(function (part) { return part.trim(); })
      .find(function (part) { return part.startsWith(cookieName + '='); });
    return cookie ? cookie.slice(cookieName.length + 1) : '';
  }

  var userId = getUser();
  if (userId !== 'admin') {
    location.replace('login.html');
    return;
  }

  var agentSelect = document.getElementById('agent-select');
  var reloadAgents = document.getElementById('reload-agents');
  var messageInput = document.getElementById('message');
  var sendButton = document.getElementById('send');
  var status = document.getElementById('status');
  var messages = document.getElementById('messages');
  var chatScroll = document.getElementById('chat-scroll');
  var welcome = document.getElementById('welcome');
  var agentDescEl = document.getElementById('agent-desc');
  var welcomeTitle = document.getElementById('welcome-title');
  var attachButton = document.getElementById('attach');
  var imageInput = document.getElementById('image-input');
  var attachmentsEl = document.getElementById('attachments');
  var welcomeText = document.getElementById('welcome-text');
  var apiEntry = document.getElementById('api-entry');
  var apiEntryTitle = document.getElementById('api-entry-title');
  var apiEntryDesc = document.getElementById('api-entry-desc');
  var apiEntryState = document.getElementById('api-entry-state');
  var sessionHint = document.getElementById('session-hint');
  var settingsModal = document.getElementById('settings-modal');
  var cfgBase = document.getElementById('cfg-base');
  var cfgKey = document.getElementById('cfg-key');
  var cfgModel = document.getElementById('cfg-model');
  var cfgStatus = document.getElementById('cfg-status');

  var state = {
    busy: false,
    sessionId: null,
    history: [],
    images: []
  };

  var MAX_IMAGES = 3;

  document.getElementById('account-name').textContent = userId;

  /* ---------------- API 配置 ---------------- */

  function loadConfig() {
    try {
      var raw = localStorage.getItem(storageKey);
      if (!raw) return null;
      var cfg = JSON.parse(raw);
      if (!cfg || !cfg.apiBaseUrl || !cfg.apiKey) return null;
      return cfg;
    } catch (e) {
      return null;
    }
  }

  function currentConfig() {
    return loadConfig();
  }

  function refreshBadge() {
    var cfg = currentConfig();
    if (cfg) {
      apiEntry.classList.add('configured');
      apiEntryTitle.textContent = '使用我的 API';
      apiEntryDesc.textContent = (cfg.model || '默认模型') + ' · 只保存在本机浏览器';
      apiEntryState.textContent = '已连接';
    } else {
      apiEntry.classList.remove('configured');
      apiEntryTitle.textContent = '接入我的 API';
      apiEntryDesc.textContent = '使用你自己的模型 Key，只保存在本机浏览器';
      apiEntryState.textContent = '未配置';
    }
    sessionHint.textContent = cfg ? '当前使用你自己的 API Key。' : '每次提问将创建新的会话。';
  }

  function apiFields() {
    var cfg = currentConfig();
    if (!cfg) return {};
    return { apiBaseUrl: cfg.apiBaseUrl, apiKey: cfg.apiKey, model: cfg.model || '' };
  }

  /* ---------------- 请求 ---------------- */

  function baseUrl() {
    if (!window.AGENT_CONFIG || typeof window.AGENT_CONFIG.apiBaseUrl !== 'string' || !window.AGENT_CONFIG.apiBaseUrl.trim()) {
      throw new Error('接口地址未配置，请检查 config.js。');
    }
    return window.AGENT_CONFIG.apiBaseUrl.replace(/\/+$/, '');
  }

  async function request(path, body) {
    var options = body === undefined ? {} : {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    };
    var response = await fetch(baseUrl() + '/api/v1/' + path, options);
    var result = null;
    try {
      result = await response.json();
    } catch (e) {
      throw new Error('接口返回异常（HTTP ' + response.status + '）。');
    }
    if (!result || result.code !== '0000') {
      throw new Error((result && result.info) || ('接口返回异常（' + result.code + '）。'));
    }
    return result.data;
  }

  /* ---------------- 渲染 ---------------- */

  function renderMarkdown(text) {
    if (window.AgentMarkdown) return window.AgentMarkdown.render(text);
    return String(text).replace(/[&<>]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]; });
  }

  function compressImage(file) {
    return new Promise(function (resolve, reject) {
      var reader = new FileReader();
      reader.onerror = function () { reject(new Error('图片读取失败')); };
      reader.onload = function () {
        var img = new Image();
        img.onerror = function () { reject(new Error('图片解析失败')); };
        img.onload = function () {
          var max = 1024;
          var scale = Math.min(1, max / Math.max(img.width, img.height));
          var canvas = document.createElement('canvas');
          canvas.width = Math.max(1, Math.round(img.width * scale));
          canvas.height = Math.max(1, Math.round(img.height * scale));
          canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height);
          var dataUrl = canvas.toDataURL('image/jpeg', 0.82);
          resolve({ mimeType: 'image/jpeg', data: dataUrl.split(',')[1], dataUrl: dataUrl });
        };
        img.src = reader.result;
      };
      reader.readAsDataURL(file);
    });
  }

  function renderAttachments() {
    attachmentsEl.replaceChildren();
    attachmentsEl.hidden = state.images.length === 0;
    state.images.forEach(function (image, index) {
      var item = document.createElement('div');
      item.className = 'attachment';
      var thumb = document.createElement('img');
      thumb.src = image.dataUrl;
      thumb.alt = '待发送图片 ' + (index + 1);
      var remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'attachment-remove';
      remove.setAttribute('aria-label', '移除图片');
      remove.textContent = '×';
      remove.addEventListener('click', function () {
        state.images.splice(index, 1);
        renderAttachments();
        updateComposerState();
      });
      item.append(thumb, remove);
      attachmentsEl.appendChild(item);
    });
  }

  function updateComposerState() {
    var canType = !state.busy && Boolean(agentSelect.value);
    attachButton.disabled = !canType || state.images.length >= MAX_IMAGES;
  }

  async function handleImagePick(event) {
    var files = Array.prototype.slice.call(event.target.files || []);
    imageInput.value = '';
    if (!files.length) return;

    var room = MAX_IMAGES - state.images.length;
    if (room <= 0) {
      status.textContent = '最多上传 ' + MAX_IMAGES + ' 张图片。';
      return;
    }

    for (var i = 0; i < Math.min(files.length, room); i++) {
      try {
        var compressed = await compressImage(files[i]);
        state.images.push(compressed);
      } catch (error) {
        status.textContent = error.message;
      }
    }
    if (files.length > room) status.textContent = '最多上传 ' + MAX_IMAGES + ' 张图片，多余的已忽略。';
    renderAttachments();
    updateComposerState();
    messageInput.focus();
  }

  function addMessage(role, name, content, stateFlag, images) {
    welcome.hidden = true;
    var item = document.createElement('article');
    item.className = 'message ' + role + (stateFlag ? ' ' + stateFlag : '');

    var avatar = document.createElement('div');
    avatar.className = 'avatar ' + (role === 'assistant' ? 'assistant-avatar' : 'user-avatar');
    avatar.setAttribute('aria-hidden', 'true');
    avatar.textContent = role === 'assistant' ? '✦' : userId.charAt(0).toUpperCase();

    var body = document.createElement('div');
    body.className = 'message-body';
    var meta = document.createElement('div');
    meta.className = 'message-meta';
    meta.textContent = name;
    var bubble = document.createElement('div');
    bubble.className = 'bubble';

    if (role === 'assistant') {
      bubble.innerHTML = renderMarkdown(content);
    } else {
      if (images && images.length) {
        var gallery = document.createElement('div');
        gallery.className = 'bubble-images';
        images.forEach(function (src) {
          var thumb = document.createElement('img');
          thumb.src = src;
          thumb.alt = '发送的图片';
          gallery.appendChild(thumb);
        });
        bubble.appendChild(gallery);
      }
      var textNode = document.createElement('span');
      textNode.textContent = content;
      bubble.appendChild(textNode);
    }

    body.append(meta, bubble);
    item.append(avatar, body);
    messages.appendChild(item);
    chatScroll.scrollTop = chatScroll.scrollHeight;
    return { item: item, bubble: bubble };
  }

  function selectedDesc() {
    var option = agentSelect.options[agentSelect.selectedIndex];
    return option && option.dataset ? (option.dataset.desc || '') : '';
  }

  function refreshAgentIntro() {
    var name = agentSelect.options.length ? agentSelect.options[agentSelect.selectedIndex].textContent : '';
    var desc = selectedDesc();
    agentDescEl.textContent = desc;
    agentDescEl.hidden = !desc;
    if (name) {
      welcomeTitle.textContent = '你好，我是' + name;
      welcomeText.textContent = desc || '有什么可以帮你？直接输入问题就能开始。';
    }
  }

  function setControls() {
    var ready = Boolean(agentSelect.value) && !state.busy;
    agentSelect.disabled = state.busy || agentSelect.options.length === 0;
    messageInput.disabled = !ready;
    sendButton.disabled = !ready;
    updateComposerState();
  }

  /* ---------------- 智能体 ---------------- */

  async function loadAgents() {
    agentSelect.replaceChildren();
    agentSelect.disabled = true;
    messageInput.disabled = true;
    sendButton.disabled = true;
    reloadAgents.hidden = true;
    status.textContent = '';
    try {
      var agents = await request('query_ai_agent_config_list');
      if (!Array.isArray(agents)) throw new Error('智能体列表格式不正确。');
      agents.forEach(function (agent) {
        if (!agent || typeof agent.agentId !== 'string' || !agent.agentId) return;
        var option = document.createElement('option');
        option.value = agent.agentId;
        option.textContent = agent.agentName || agent.agentId;
        if (agent.agentDesc) {
          option.title = agent.agentDesc;
          option.dataset.desc = agent.agentDesc;
        }
        agentSelect.appendChild(option);
      });
      if (!agents.length) {
        status.textContent = '暂无可用智能体，请检查服务配置。';
        reloadAgents.hidden = false;
      }
    } catch (error) {
      agentSelect.replaceChildren();
      status.textContent = '加载智能体失败：' + error.message;
      reloadAgents.hidden = false;
    }
    setControls();
    refreshAgentIntro();
  }

  /* ---------------- 会话 ---------------- */

  async function ensureSession() {
    if (state.sessionId) return state.sessionId;
    var data = await request('create_session', Object.assign({
      agentId: agentSelect.value,
      userId: userId
    }, apiFields()));
    if (!data || !data.sessionId) throw new Error('创建会话失败，请稍后重试。');
    state.sessionId = data.sessionId;
    return state.sessionId;
  }

  function resetChat() {
    state.sessionId = null;
    state.history = [];
    state.images = [];
    renderAttachments();
    updateComposerState();
    messages.replaceChildren();
    welcome.hidden = false;
    status.textContent = '';
  }

  /* ---------------- 发送（流式） ---------------- */

  async function streamReply(payload, onChunk) {
    var response = await fetch(baseUrl() + '/api/v1/chat_stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    if (!response.ok || !response.body) {
      var info = '';
      try {
        var parsed = await response.json();
        info = parsed && parsed.info ? parsed.info : '';
      } catch (e) { /* ignore */ }
      if (info) throw new Error(info);
      if (response.status === 500) throw new Error('服务端处理失败，请稍后重试');
      throw new Error('流式请求失败（HTTP ' + response.status + '）');
    }

    var reader = response.body.getReader();
    var decoder = new TextDecoder('utf-8');
    var full = '';

    while (true) {
      var chunk = await reader.read();
      if (chunk.done) break;
      var text = decoder.decode(chunk.value, { stream: true });
      if (!text) continue;
      full += text;
      onChunk(text, full);
    }
    return full;
  }

  async function submitMessage(event) {
    event.preventDefault();
    var message = messageInput.value.trim();
    if (state.busy || !agentSelect.value || !message) return;

    var agentName = agentSelect.options[agentSelect.selectedIndex].textContent;

    state.busy = true;
    setControls();
    status.textContent = '';

    var pendingImages = state.images.slice();
    state.images = [];
    renderAttachments();

    addMessage('user', userId, message, null, pendingImages.map(function (img) { return img.dataUrl; }));
    state.history.push({ role: 'user', name: userId, text: message, images: pendingImages.length });
    messageInput.value = '';
    messageInput.style.height = '';

    var reply = addMessage('assistant', agentName, '', 'pending');
    reply.bubble.classList.add('streaming');

    try {
      var sessionId = await ensureSession();

      var payload = Object.assign({
        agentId: agentSelect.value,
        userId: userId,
        sessionId: sessionId,
        message: message
      }, apiFields());

      var finalText = '';

      if (pendingImages.length) {
        // 带图片：走非流式接口，图片以 base64 内联传输
        payload.images = pendingImages.map(function (img) {
          return { mimeType: img.mimeType, data: img.data };
        });
        var data = await request('chat', payload);
        finalText = (data && data.content) ? data.content : '';
        reply.bubble.classList.remove('streaming');
        reply.bubble.innerHTML = renderMarkdown(finalText || '（无回复内容）');
      } else {
        var full = '';
        finalText = await streamReply(payload, function (_, accumulated) {
          full = accumulated;
          reply.bubble.textContent = accumulated;
          chatScroll.scrollTop = chatScroll.scrollHeight;
        });
        reply.bubble.classList.remove('streaming');
        reply.bubble.innerHTML = renderMarkdown(finalText || full || '（无回复内容）');
        finalText = finalText || full;
      }

      reply.item.classList.remove('pending');
      state.history.push({ role: 'assistant', name: agentName, text: finalText });
    } catch (error) {
      reply.item.classList.remove('pending');
      reply.bubble.classList.remove('streaming');
      reply.item.classList.add('error');
      reply.bubble.textContent = '请求失败：' + error.message;
      status.textContent = '请检查 API 配置或稍后重试。';
      messageInput.value = message;
    } finally {
      state.busy = false;
      setControls();
      chatScroll.scrollTop = chatScroll.scrollHeight;
      messageInput.focus();
    }
  }

  /* ---------------- 对话管理 ---------------- */

  function newChat() {
    if (state.history.length && !window.confirm('确定要开始新对话吗？当前对话会被清空。')) return;
    resetChat();
    messageInput.focus();
  }

  function exportChat() {
    if (!state.history.length) {
      status.textContent = '当前还没有对话内容可以导出。';
      return;
    }
    var lines = ['# AI Agent 对话记录', '', '导出时间：' + new Date().toLocaleString('zh-CN'), ''];
    state.history.forEach(function (item) {
      lines.push('### ' + (item.role === 'user' ? '我' : item.name));
      lines.push('');
      lines.push(item.images ? item.text + '（含 ' + item.images + ' 张图片）' : item.text);
      lines.push('');
      lines.push('---');
      lines.push('');
    });

    var blob = new Blob([lines.join('\n')], { type: 'text/markdown;charset=utf-8' });
    var url = URL.createObjectURL(blob);
    var link = document.createElement('a');
    link.href = url;
    link.download = 'chat-' + new Date().toISOString().slice(0, 16).replace(/[:T]/g, '-') + '.md';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  /* ---------------- 设置弹窗 ---------------- */

  function openSettings() {
    var cfg = currentConfig();
    cfgBase.value = cfg ? cfg.apiBaseUrl : '';
    cfgKey.value = cfg ? cfg.apiKey : '';
    cfgModel.value = cfg ? (cfg.model || '') : '';
    cfgStatus.textContent = '';
    cfgStatus.className = 'modal-status';
    settingsModal.hidden = false;
    cfgBase.focus();
  }

  function closeSettings() {
    settingsModal.hidden = true;
  }

  function readFormConfig() {
    return {
      apiBaseUrl: cfgBase.value.trim(),
      apiKey: cfgKey.value.trim(),
      model: cfgModel.value.trim()
    };
  }

  async function testConnection() {
    var cfg = readFormConfig();
    if (!cfg.apiBaseUrl || !cfg.apiKey) {
      cfgStatus.textContent = '请先填写 Base URL 和 API Key。';
      cfgStatus.className = 'modal-status error';
      return;
    }
    cfgStatus.textContent = '正在测试连接...';
    cfgStatus.className = 'modal-status';
    try {
      var reply = await request('test_api', cfg);
      cfgStatus.textContent = '连接成功' + (reply ? '：' + reply : '。');
      cfgStatus.className = 'modal-status ok';
    } catch (error) {
      cfgStatus.textContent = '连接失败：' + error.message;
      cfgStatus.className = 'modal-status error';
    }
  }

  function saveConfig() {
    var cfg = readFormConfig();
    if (!cfg.apiBaseUrl || !cfg.apiKey) {
      cfgStatus.textContent = 'Base URL 与 API Key 都必须填写。';
      cfgStatus.className = 'modal-status error';
      return;
    }
    localStorage.setItem(storageKey, JSON.stringify(cfg));
    refreshBadge();
    cfgStatus.textContent = '已保存，本次对话将从下一轮开始使用你的配置。';
    cfgStatus.className = 'modal-status ok';
    resetChat();
  }

  function clearConfig() {
    localStorage.removeItem(storageKey);
    cfgBase.value = '';
    cfgKey.value = '';
    cfgModel.value = '';
    refreshBadge();
    cfgStatus.textContent = '已清除，将使用服务端默认模型。';
    cfgStatus.className = 'modal-status ok';
    resetChat();
  }

  /* ---------------- 事件绑定 ---------------- */

  document.getElementById('chat-form').addEventListener('submit', submitMessage);
  agentSelect.addEventListener('change', function () {
    // 切换智能体后上下文不再通用，重置会话（保留已展示的对话记录）
    state.sessionId = null;
    refreshAgentIntro();
    setControls();
    if (state.history.length) {
      status.textContent = '已切换智能体，下一轮将开启新的上下文。';
    }
  });
  reloadAgents.addEventListener('click', loadAgents);
  document.getElementById('new-chat').addEventListener('click', newChat);
  document.getElementById('export-chat').addEventListener('click', exportChat);
  apiEntry.addEventListener('click', openSettings);
  document.getElementById('settings-close').addEventListener('click', closeSettings);
  document.getElementById('cfg-test').addEventListener('click', testConnection);
  document.getElementById('cfg-save').addEventListener('click', saveConfig);
  document.getElementById('cfg-clear').addEventListener('click', clearConfig);
  settingsModal.addEventListener('click', function (event) {
    if (event.target === settingsModal) closeSettings();
  });
  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && !settingsModal.hidden) closeSettings();
  });

  attachButton.addEventListener('click', function () { imageInput.click(); });
  imageInput.addEventListener('change', handleImagePick);

  messageInput.addEventListener('input', function () {
    this.style.height = 'auto';
    this.style.height = Math.min(this.scrollHeight, 150) + 'px';
  });
  messageInput.addEventListener('keydown', function (event) {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      document.getElementById('chat-form').requestSubmit();
    }
  });
  document.getElementById('logout').addEventListener('click', function () {
    var secure = location.protocol === 'https:' ? '; Secure' : '';
    document.cookie = cookieName + '=; Path=/; Max-Age=0; SameSite=Strict' + secure;
    if (getUser()) {
      status.textContent = '无法清除登录信息，请检查浏览器 Cookie 设置。';
      return;
    }
    location.replace('login.html');
  });

  refreshBadge();
  loadAgents();
}());
