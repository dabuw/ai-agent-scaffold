const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const htmlDir = path.join(__dirname, '../html');
const chatScript = fs.readFileSync(path.join(htmlDir, 'chat.js'), 'utf8');
const loginScript = fs.readFileSync(path.join(htmlDir, 'login.html'), 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1];

class Element {
  constructor() {
    this.children = [];
    this.listeners = {};
    this.style = {};
    this.className = '';
    this.textContent = '';
    this.innerHTML = '';
    this.value = '';
    this.hidden = false;
    this.disabled = false;
    this.selectedIndex = 0;
    this.classList = {
      add: name => { if (!this.className.split(' ').includes(name)) this.className += ' ' + name; },
      remove: name => { this.className = this.className.split(' ').filter(part => part !== name).join(' '); }
    };
  }
  get options() { return this.children; }
  appendChild(child) {
    this.children.push(child);
    if (this.children.length === 1 && child.value) this.value = child.value;
    return child;
  }
  append(...children) { children.forEach(child => this.appendChild(child)); }
  replaceChildren(...children) { this.children = []; this.value = ''; this.append(...children); }
  addEventListener(event, listener) { this.listeners[event] = listener; }
  setAttribute() {}
  focus() {}
  click() { if (this.listeners.click) this.listeners.click({ target: this }); }
}

function setup(script, { cookie = '', responses = [], storage = {} } = {}) {
  const ids = [
    'agent-select', 'reload-agents', 'message', 'send', 'status', 'messages', 'chat-scroll', 'welcome',
    'account-name', 'chat-form', 'logout', 'api-entry', 'api-entry-title', 'api-entry-desc', 'api-entry-state',
    'session-hint', 'new-chat', 'export-chat', 'settings-modal', 'settings-close', 'cfg-base', 'cfg-key', 'cfg-model', 'cfg-status',
    'cfg-test', 'cfg-save', 'cfg-clear',
    'login-form', 'account', 'password', 'login-error', 'toggle-pw'
  ];
  const elements = Object.fromEntries(ids.map(id => [id, new Element()]));
  elements.password.type = 'password';
  elements['settings-modal'].hidden = true;

  let storedCookie = cookie;
  const document = {
    get cookie() { return storedCookie; },
    set cookie(value) { storedCookie = value.includes('Max-Age=0;') ? '' : value.split(';')[0]; },
    getElementById: id => elements[id],
    createElement: () => new Element(),
    addEventListener() {},
    body: new Element()
  };
  const location = { protocol: 'http:', replace(url) { this.redirect = url; } };

  const calls = [];
  const fetch = async (url, options) => {
    calls.push({ url, options });
    const result = responses.shift();
    if (result instanceof Error) throw result;
    assert.ok(result, 'unexpected request: ' + url);

    if (typeof result === 'object' && result.stream !== undefined) {
      const bytes = new TextEncoder().encode(result.stream);
      let read = false;
      return {
        ok: true,
        body: {
          getReader() {
            return {
              async read() {
                if (read) return { done: true, value: undefined };
                read = true;
                return { done: false, value: bytes };
              }
            };
          }
        }
      };
    }
    return { ok: true, json: async () => result };
  };

  const localStorage = {
    getItem: key => (Object.prototype.hasOwnProperty.call(storage, key) ? storage[key] : null),
    setItem: (key, value) => { storage[key] = value; },
    removeItem: key => { delete storage[key]; }
  };

  const window = {
    AGENT_CONFIG: { apiBaseUrl: 'http://127.0.0.1:8091/' },
    confirm: () => true,
    localStorage
  };
  vm.runInNewContext(script, { document, location, fetch, window, localStorage, TextDecoder, console, Date, URL, Blob });

  return { elements, location, calls, storage, window };
}

const success = data => ({ code: '0000', data });
const tick = () => new Promise(resolve => setImmediate(resolve));
const submit = element => element.listeners.submit({ preventDefault() {} });

test('login redirects only after accepting the demo credentials', () => {
  const { elements, location } = setup(loginScript);
  elements.account.value = 'admin';
  elements.password.value = 'wrong';
  submit(elements['login-form']);
  assert.match(elements['login-error'].textContent, /不正确/);
  assert.equal(location.redirect, undefined);
  elements.password.value = 'admin';
  submit(elements['login-form']);
  assert.equal(location.redirect, 'index.html');
});

test('unauthenticated users are redirected without calling the API', () => {
  const { location, calls } = setup(chatScript);
  assert.equal(location.redirect, 'login.html');
  assert.equal(calls.length, 0);
});

test('loads agents, streams a reply and reuses the session on the next turn', async () => {
  const { elements, calls } = setup(chatScript, {
    cookie: 'ai_agent_demo_user=admin',
    responses: [
      success([{ agentId: 'a', agentName: '助手' }]),
      success({ sessionId: 's1' }),
      { stream: '你好，我是助手' }
    ]
  });
  await tick();
  assert.equal(elements['agent-select'].options[0].textContent, '助手');

  elements.message.value = '你好';
  await submit(elements['chat-form']);

  assert.equal(calls[1].url, 'http://127.0.0.1:8091/api/v1/create_session');
  assert.equal(calls[2].url, 'http://127.0.0.1:8091/api/v1/chat_stream');
  assert.deepEqual(JSON.parse(calls[2].options.body), { agentId: 'a', userId: 'admin', sessionId: 's1', message: '你好' });

  const assistant = elements.messages.children[1];
  assert.equal(assistant.className.trim(), 'message assistant');
  assert.match(assistant.children[1].children[1].innerHTML, /你好，我是助手/);

  // 第二轮不应再次创建会话
  const before = calls.length;
  elements.message.value = '继续';
  await submit(elements['chat-form']);
  assert.equal(calls.length, before + 1);
  assert.equal(JSON.parse(calls[calls.length - 1].options.body).sessionId, 's1');
});

test('failed session creation keeps the draft and shows the reason', async () => {
  const { elements, calls } = setup(chatScript, {
    cookie: 'ai_agent_demo_user=admin',
    responses: [success([{ agentId: 'a', agentName: '助手' }]), { code: 'E0001', info: '智能体不存在' }]
  });
  await tick();
  elements.message.value = '你好';
  await submit(elements['chat-form']);
  assert.equal(calls.length, 2);
  assert.equal(elements.message.value, '你好');
  assert.match(elements.messages.children[1].children[1].children[1].textContent, /智能体不存在/);
});

test('agent loading errors can be retried', async () => {
  const { elements } = setup(chatScript, {
    cookie: 'ai_agent_demo_user=admin',
    responses: [new Error('网络不可用'), success([{ agentId: 'a', agentName: '助手' }])]
  });
  await tick();
  assert.match(elements.status.textContent, /网络不可用/);
  assert.equal(elements['reload-agents'].hidden, false);
  await elements['reload-agents'].listeners.click();
  assert.equal(elements['agent-select'].value, 'a');
  assert.equal(elements.send.disabled, false);
});

test('saved API config is sent with every request and shown in the badge', async () => {
  const storage = {};
  const { elements, calls } = setup(chatScript, {
    cookie: 'ai_agent_demo_user=admin',
    storage,
    responses: [
      success([{ agentId: 'a', agentName: '助手' }]),
      success({ sessionId: 's1' }),
      { stream: '收到' }
    ]
  });
  await tick();

  elements['api-entry'].listeners.click();
  elements['cfg-base'].value = 'https://api.deepseek.com';
  elements['cfg-key'].value = 'sk-test';
  elements['cfg-model'].value = 'deepseek-chat';
  elements['cfg-save'].listeners.click();

  assert.equal(JSON.parse(storage['ai_agent_api_config']).apiKey, 'sk-test');
  assert.match(elements['api-entry'].className, /configured/);
  assert.equal(elements['api-entry-state'].textContent, '已连接');
  assert.match(elements['api-entry-desc'].textContent, /deepseek-chat/);

  elements.message.value = '你好';
  await submit(elements['chat-form']);

  const createBody = JSON.parse(calls[1].options.body);
  assert.deepEqual(createBody, { agentId: 'a', userId: 'admin', apiBaseUrl: 'https://api.deepseek.com', apiKey: 'sk-test', model: 'deepseek-chat' });
});

test('clearing the config falls back to the default agent', async () => {
  const storage = { 'ai_agent_api_config': JSON.stringify({ apiBaseUrl: 'https://api.deepseek.com', apiKey: 'sk-test', model: 'deepseek-chat' }) };
  const { elements, calls } = setup(chatScript, {
    cookie: 'ai_agent_demo_user=admin',
    storage,
    responses: [success([{ agentId: 'a', agentName: '助手' }]), success({ sessionId: 's1' }), { stream: 'ok' }]
  });
  await tick();
  assert.match(elements['api-entry'].className, /configured/);

  elements['api-entry'].listeners.click();
  elements['cfg-clear'].listeners.click();
  assert.equal(storage['ai_agent_api_config'], undefined);
  assert.doesNotMatch(elements['api-entry'].className, /configured/);
  assert.equal(elements['api-entry-state'].textContent, '未配置');

  elements.message.value = '你好';
  await submit(elements['chat-form']);
  assert.deepEqual(JSON.parse(calls[1].options.body), { agentId: 'a', userId: 'admin' });
});
