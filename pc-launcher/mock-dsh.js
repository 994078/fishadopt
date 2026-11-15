// 模拟 DSH（小肥鱼）—— 用于 PC 版启动器在没有真实 DSH 时的开箱测试
//
// 监听 DSH_PORT（默认 51146），返回一个极简对话界面，让"一键领养 → 端口探测 → iframe 加载"完整走通。
// 配置了真实 DSH 时（DSH_COMMAND 指向真实启动命令），无需本文件。
'use strict';

const http = require('http');

const PORT = parseInt(process.env.DSH_PORT || process.env.PORT || '51146', 10);

const html = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>小肥鱼 · 对话界面（模拟）</title>
<style>
  body { font-family: -apple-system, "Segoe UI", "Microsoft YaHei", sans-serif; margin: 0; background: #f5f6f8; color: #1a1a1a; }
  header { background: #1e7bff; color: #fff; padding: 14px 20px; font-size: 18px; }
  main { max-width: 720px; margin: 0 auto; padding: 24px; }
  .card { background: #fff; border-radius: 10px; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,.08); }
  .hint { color: #666; font-size: 13px; margin-top: 12px; }
  .key { background: #eef3ff; border-radius: 6px; padding: 8px 10px; font-family: monospace; font-size: 13px; word-break: break-all; }
  input[type=text] { width: 100%; box-sizing: border-box; padding: 10px; border: 1px solid #d0d5dd; border-radius: 6px; font-size: 15px; }
  button { margin-top: 10px; background: #1e7bff; color: #fff; border: 0; border-radius: 6px; padding: 10px 18px; font-size: 15px; cursor: pointer; }
  button:disabled { background: #a0b8e0; cursor: not-allowed; }
  #chat { margin-top: 16px; }
  .msg { padding: 8px 0; border-bottom: 1px solid #eee; }
</style>
</head>
<body>
<header>小肥鱼 · 对话界面</header>
<main>
  <div class="card">
    <p>这是一个<b>模拟的 DSH 前端页面</b>，用于验证「一键领养小肥鱼」PC 版启动器的完整链路。</p>
    <p class="hint">容器注入的环境变量 DSH_KEY / DSH_PORT：</p>
    <div class="key" id="env"></div>
    <p class="hint">配置真实 DSH 后，此 iframe 将替换为 DSH 自带的真实界面。</p>
  </div>
  <div class="card" style="margin-top:16px">
    <input type="text" id="in" placeholder="输入消息（模拟，不会真正发送）">
    <button id="send" onclick="send()">发送</button>
    <div id="chat"></div>
  </div>
</main>
<script>
  document.getElementById('env').textContent = '__DSH_KEY_MASKED__';
  function send() {
    var input = document.getElementById('in');
    var chat = document.getElementById('chat');
    if (!input.value.trim()) return;
    var div = document.createElement('div');
    div.className = 'msg';
    div.textContent = '你：' + input.value;
    chat.appendChild(div);
    var reply = document.createElement('div');
    reply.className = 'msg';
    reply.textContent = '小肥鱼（模拟）：这是一条模拟回复。接入真实 DSH 后此处为真实对话。';
    chat.appendChild(reply);
    input.value = '';
  }
</script>
</body>
</html>`;

// 掩码显示已注入的密钥（仅作演示，不泄露明文）
function maskKey(k) {
  if (!k) return '(未注入，请在设置页填写 API Key)';
  if (k.length <= 8) return '****';
  return k.slice(0, 4) + '****' + k.slice(-2);
}
const maskedDisplay = maskKey(process.env.DSH_KEY || '');

const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(html.replace('__DSH_KEY_MASKED__', maskedDisplay));
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`[mock-dsh] 模拟小肥鱼已就绪：http://127.0.0.1:${PORT}`);
});
