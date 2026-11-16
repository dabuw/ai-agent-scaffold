# AI Agent 对话页面

通过 Nginx 等 HTTP 服务发布 `html` 目录，访问 `login.html`。演示账号和密码均为 `admin`。登录状态保存在浏览器 Cookie 中，退出登录会清除该 Cookie。

在 `html/config.js` 中设置后端根地址，默认是 `http://127.0.0.1:8091`；地址由访问页面的浏览器解析，远程访问时应改为浏览器能够访问的后端地址。若页面通过 HTTPS 提供，后端也需要使用 HTTPS，避免浏览器阻止混合内容请求。

## 功能

- **自带 API Key**：右上角齿轮打开「API 设置」，填写 Base URL / API Key / 模型后可先「测试连接」再保存。配置保存在浏览器 `localStorage`，每次请求随请求体传给后端，后端用完即弃、不落库、不写日志。不填写时回落到服务端 yml 里的默认模型。
- **多轮对话**：首轮创建会话，后续消息复用同一个 `sessionId`，模型可以记住上下文。
- **流式输出**：对话走 `chat_stream` 接口，回复逐字出现。
- **Markdown 渲染**：标题、列表、引用、表格、代码块（含简易语法高亮）、链接都会被正确渲染；渲染前统一做 HTML 转义。
- **对话管理**：顶部支持新建对话、导出为 Markdown 文件。
- **错误提示**：Key 无效、无权限、额度不足、限流、超时等会被翻译成中文提示。

## 接口

| 接口 | 说明 |
|---|---|
| `GET /api/v1/query_ai_agent_config_list` | 智能体列表 |
| `POST /api/v1/create_session` | 创建会话（可带 `apiBaseUrl` / `apiKey` / `model`） |
| `POST /api/v1/chat` | 非流式对话（返回完整内容 + `sessionId`） |
| `POST /api/v1/chat_stream` | 流式对话（返回原始文本流） |
| `POST /api/v1/test_api` | 连通性测试 |

## 注意

- 服务端对每个 `userId` 限制每分钟 30 次对话，超出返回「请求过于频繁」。
- 用户的 API Key 只在浏览器与服务端之间传输，**上线务必使用 HTTPS**，否则 Key 会在网络链路明文暴露。
- 登录仍是前端演示（Cookie 校验、无服务端鉴权），公网部署前建议补上真实认证与接口鉴权。

可使用 `node --test docs/dev-ops/nginx/test/chat.test.js` 运行页面逻辑测试。
