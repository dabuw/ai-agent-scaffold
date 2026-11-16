(function () {
  'use strict';

  const cookieName = 'ai_agent_demo_user';

  function getUser() {
    const cookie = document.cookie.split(';').map(part => part.trim())
      .find(part => part.startsWith(cookieName + '='));
    return cookie ? cookie.slice(cookieName.length + 1) : '';
  }

  const userId = getUser();
  if (userId !== 'admin') {
    location.replace('login.html');
    return;
  }

  const agentSelect = document.getElementById('agent-select');
  const reloadAgents = document.getElementById('reload-agents');
  const messageInput = document.getElementById('message');
  const sendButton = document.getElementById('send');
  const status = document.getElementById('status');
  const messages = document.getElementById('messages');
  const chatScroll = document.getElementById('chat-scroll');
  const welcome = document.getElementById('welcome');
  let busy = false;

  document.getElementById('account-name').textContent = userId;

  function setControls() {
    const ready = Boolean(agentSelect.value) && !busy;
    agentSelect.disabled = busy || agentSelect.options.length === 0;
    messageInput.disabled = !ready;
    sendButton.disabled = !ready;
  }

  async function request(path, body) {
    if (!window.AGENT_CONFIG || typeof window.AGENT_CONFIG.apiBaseUrl !== 'string' || !window.AGENT_CONFIG.apiBaseUrl.trim()) {
      throw new Error('接口地址未配置，请检查 config.js。');
    }
    const base = window.AGENT_CONFIG.apiBaseUrl.replace(/\/+$/, '');
    const options = body === undefined ? {} : {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    };
    const response = await fetch(base + '/api/v1/' + path, options);
    if (!response.ok) throw new Error('接口请求失败（HTTP ' + response.status + '）。');
    const result = await response.json();
    if (!result || result.code !== '0000') {
      throw new Error(result && result.info ? result.info : '接口返回异常，请稍后重试。');
    }
    return result.data;
  }

  async function loadAgents() {
    agentSelect.replaceChildren();
    agentSelect.disabled = true;
    messageInput.disabled = true;
    sendButton.disabled = true;
    reloadAgents.hidden = true;
    status.textContent = '';
    try {
      const agents = await request('query_ai_agent_config_list');
      if (!Array.isArray(agents)) throw new Error('智能体列表格式不正确。');
      for (const agent of agents) {
        if (!agent || typeof agent.agentId !== 'string' || !agent.agentId || typeof agent.agentName !== 'string' || !agent.agentName) {
          throw new Error('智能体信息不完整，请检查服务配置。');
        }
        const option = document.createElement('option');
        option.value = agent.agentId;
        option.textContent = agent.agentName;
        agentSelect.appendChild(option);
      }
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
  }

  function addMessage(role, name, content, state) {
    welcome.hidden = true;
    const item = document.createElement('article');
    item.className = 'message ' + role + (state ? ' ' + state : '');
    const avatar = document.createElement('div');
    avatar.className = 'avatar ' + (role === 'assistant' ? 'assistant-avatar' : 'user-avatar');
    avatar.setAttribute('aria-hidden', 'true');
    avatar.textContent = role === 'assistant' ? '✦' : userId.charAt(0).toUpperCase();
    const body = document.createElement('div');
    body.className = 'message-body';
    const meta = document.createElement('div');
    meta.className = 'message-meta';
    meta.textContent = name;
    const bubble = document.createElement('div');
    bubble.className = 'bubble';
    bubble.textContent = content;
    body.append(meta, bubble);
    item.append(avatar, body);
    messages.appendChild(item);
    chatScroll.scrollTop = chatScroll.scrollHeight;
    return { item, bubble };
  }

  async function submitMessage(event) {
    event.preventDefault();
    const message = messageInput.value.trim();
    if (busy || !agentSelect.value || !message) return;
    if (getUser() !== userId) {
      location.replace('login.html');
      return;
    }
    const agentId = agentSelect.value;
    const agentName = agentSelect.options[agentSelect.selectedIndex].textContent;
    busy = true;
    setControls();
    status.textContent = '';
    addMessage('user', userId, message);
    messageInput.value = '';
    messageInput.style.height = '';
    const reply = addMessage('assistant', agentName, '正在思考...', 'pending');
    try {
      const session = await request('create_session', { agentId, userId });
      if (!session || typeof session.sessionId !== 'string' || !session.sessionId) {
        throw new Error('创建会话未返回有效的会话 ID。');
      }
      const response = await request('chat', { agentId, userId, sessionId: session.sessionId, message });
      if (!response || typeof response.content !== 'string' || !response.content.trim()) {
        throw new Error('智能体未返回有效的回复。');
      }
      reply.bubble.textContent = response.content;
      reply.item.classList.remove('pending');
    } catch (error) {
      reply.item.classList.remove('pending');
      reply.item.classList.add('error');
      reply.bubble.textContent = '请求失败：' + error.message;
      status.textContent = '请检查服务状态后重新发送。';
      messageInput.value = message;
    } finally {
      busy = false;
      setControls();
      chatScroll.scrollTop = chatScroll.scrollHeight;
      messageInput.focus();
    }
  }

  document.getElementById('chat-form').addEventListener('submit', submitMessage);
  agentSelect.addEventListener('change', setControls);
  reloadAgents.addEventListener('click', loadAgents);
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
    const secure = location.protocol === 'https:' ? '; Secure' : '';
    document.cookie = cookieName + '=; Path=/; Max-Age=0; SameSite=Strict' + secure;
    if (getUser()) {
      status.textContent = '无法清除登录信息，请检查浏览器 Cookie 设置。';
      return;
    }
    location.replace('login.html');
  });

  loadAgents();
}());
