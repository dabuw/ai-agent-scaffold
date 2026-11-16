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
    this.value = '';
    this.hidden = false;
    this.selectedIndex = 0;
    this.classList = {
      add: name => { this.className += ' ' + name; },
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
}

function setup(script, { cookie = '', responses = [] } = {}) {
  const ids = ['agent-select', 'reload-agents', 'message', 'send', 'status', 'messages', 'chat-scroll',
    'welcome', 'account-name', 'chat-form', 'logout', 'login-form', 'username', 'password', 'login-error', 'toggle-password'];
  const elements = Object.fromEntries(ids.map(id => [id, new Element()]));
  elements.password.type = 'password';
  let storedCookie = cookie;
  const document = {
    get cookie() { return storedCookie; },
    set cookie(value) { storedCookie = value.includes('Max-Age=0;') ? '' : value.split(';')[0]; },
    getElementById: id => elements[id],
    createElement: () => new Element()
  };
  const location = { protocol: 'http:', replace(url) { this.redirect = url; } };
  const calls = [];
  const fetch = async (url, options) => {
    calls.push({ url, options });
    const result = responses.shift();
    if (result instanceof Error) throw result;
    assert.ok(result, 'unexpected request: ' + url);
    return { ok: true, json: async () => result };
  };
  vm.runInNewContext(script, { document, location, fetch, window: { AGENT_CONFIG: { apiBaseUrl: 'http://127.0.0.1:8091/' } } });
  return { elements, location, calls };
}

const success = data => ({ code: '0000', data });
const tick = () => new Promise(resolve => setImmediate(resolve));
const submit = element => element.listeners.submit({ preventDefault() {} });

test('login redirects only after accepting the demo credentials', () => {
  const { elements, location } = setup(loginScript);
  elements.username.value = 'admin';
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

test('loads agents, creates a session for each question, and renders text safely', async () => {
  const { elements, calls, location } = setup(chatScript, {
    cookie: 'ai_agent_demo_user=admin',
    responses: [
      success([{ agentId: 'a', agentName: '助手' }, { agentId: 'b', agentName: '另一个助手' }]),
      success({ sessionId: 's1' }), success({ content: '<b>回复</b>' }),
      success({ sessionId: 's2' }), success({ content: '第二条回复' })
    ]
  });
  await tick();
  assert.equal(elements['agent-select'].options[0].textContent, '助手');
  elements.message.value = '你好';
  await submit(elements['chat-form']);
  assert.equal(calls[0].url, 'http://127.0.0.1:8091/api/v1/query_ai_agent_config_list');
  assert.deepEqual(JSON.parse(calls[1].options.body), { agentId: 'a', userId: 'admin' });
  assert.deepEqual(JSON.parse(calls[2].options.body), { agentId: 'a', userId: 'admin', sessionId: 's1', message: '你好' });
  assert.equal(elements.messages.children[0].className, 'message user');
  assert.equal(elements.messages.children[1].className, 'message assistant');
  assert.equal(elements.messages.children[1].children[1].children[1].textContent, '<b>回复</b>');
  elements['agent-select'].value = 'b';
  elements['agent-select'].selectedIndex = 1;
  elements.message.value = '再次提问';
  await submit(elements['chat-form']);
  assert.deepEqual(JSON.parse(calls[3].options.body), { agentId: 'b', userId: 'admin' });
  assert.deepEqual(JSON.parse(calls[4].options.body), { agentId: 'b', userId: 'admin', sessionId: 's2', message: '再次提问' });
  elements.logout.listeners.click();
  assert.equal(location.redirect, 'login.html');
});

test('failed requests preserve the draft and never send chat without a session', async () => {
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
  assert.equal(elements.send.disabled, false);
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
