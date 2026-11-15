// 一键领养小肥鱼 —— PC 版启动器（后端服务）
//
// 复刻安卓 APP 的"环境封装器"职责，在 PC 上即可完整测试「一键领养」流程：
//   外壳层(Kotlin)  ↔  本文件(Node 服务)
//   容器运行时(proot) ↔ 本机直接运行 DSH（无需 proot）
//   WebView        ↔  前端 iframe
//
// 无外部依赖（仅用 Node 内置模块），直接 `node server.js` 即可运行。
'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');
const net = require('net');
const { spawn } = require('child_process');

// ---- 可配置项（环境变量）----
const LAUNCHER_PORT = parseInt(process.env.LAUNCHER_PORT || '8080', 10); // 启动器 UI 端口
const DSH_PORT = parseInt(process.env.DSH_PORT || '51146', 10);          // 容器内 DSH 端口（演示避开 51145）
const DSH_COMMAND = process.env.DSH_COMMAND || `node "${path.join(__dirname, 'mock-dsh.js')}"`; // DSH 启动命令
const DATA_DIR = path.join(__dirname, 'data');
const KEY_FILE = path.join(DATA_DIR, 'api_key.json');
const PUBLIC_DIR = path.join(__dirname, 'public');

// ---- 日志脱敏（与 Android 版 SensitiveMasker 对齐）----
function mask(text) {
  return String(text)
    .replace(/sk-[A-Za-z0-9_\-]{6,}/g, '***REDACTED***')
    .replace(/(authorization\s*[:=]\s*(?:bearer\s+)?)([^\s,;]+)/gi, '$1***REDACTED***')
    .replace(/(cookie\s*[:=]\s*)([^\r\n;]+)/gi, '$1***REDACTED***')
    .replace(/(api[_-]?key\s*[:=]\s*)([^\s,;]+)/gi, '$1***REDACTED***');
}

// ---- 运行状态（对齐 EnvironmentState / ServiceState）----
const state = {
  environmentState: 'uninitialized', // uninitialized | preparing | ready | corrupted
  serviceState: 'stopped',           // stopped | starting | running | stopping | failed
  stage: null,
  progress: 0,
  port: DSH_PORT,
};

let logs = []; // {ts, level, message}
function log(level, message) {
  const entry = { ts: Date.now(), level, message: mask(message) };
  logs.push(entry);
  if (logs.length > 1000) logs = logs.slice(logs.length - 1000);
  console.log(`[${level.toUpperCase()}] ${entry.message}`);
}
function clearLogs() { logs = []; }

let dshProc = null;
let restartGuard = { consecutive: 0, last: 0 };

// ---- API Key（PC 演示存本地文件；Android 版用 EncryptedSharedPreferences）----
function loadKey() {
  try { return JSON.parse(fs.readFileSync(KEY_FILE, 'utf8')).key || null; } catch { return null; }
}
function saveKey(k) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
  fs.writeFileSync(KEY_FILE, JSON.stringify({ key: String(k).trim() }));
}
function clearKey() { try { fs.unlinkSync(KEY_FILE); } catch {} }
function maskedKey(k) {
  if (!k) return null;
  if (k.length <= 8) return '****';
  return k.slice(0, 4) + '****' + k.slice(-2);
}

// ---- 端口探测（对齐 PortProbe）----
function isPortOpen(port, timeout = 1000) {
  return new Promise((resolve) => {
    const socket = new net.Socket();
    const done = (ok) => { socket.destroy(); resolve(ok); };
    socket.setTimeout(timeout);
    socket.once('connect', () => done(true));
    socket.once('timeout', () => done(false));
    socket.once('error', () => done(false));
    socket.connect(port, '127.0.0.1');
  });
}
async function waitForPort(port, timeout = 60000, interval = 500) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    if (await isPortOpen(port)) return true;
    await new Promise((r) => setTimeout(r, interval));
  }
  return isPortOpen(port);
}

// 命令行分词：支持带引号（含空格）的参数
function splitCommand(cmd) {
  const tokens = [];
  const re = /"([^"]*)"|'([^']*)'|(\S+)/g;
  let m;
  while ((m = re.exec(cmd)) !== null) tokens.push(m[1] ?? m[2] ?? m[3]);
  return tokens;
}

// ---- DSH 进程管理（对齐 ProotProcessManager）----
function startDsh() {
  const apiKey = loadKey();
  const args = splitCommand(DSH_COMMAND);
  const cmd = args[0];
  const cmdArgs = args.slice(1);
  log('info', `准备启动 DSH：${cmd} ${cmdArgs.join(' ')}`);
  const child = spawn(cmd, cmdArgs, {
    cwd: __dirname,
    env: {
      ...process.env,
      DSH_KEY: apiKey || '',        // 密钥经环境变量注入，不落盘
      DSH_PORT: String(DSH_PORT),
      PORT: String(DSH_PORT),
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  dshProc = child;
  child.stdout.on('data', (d) => log('info', d.toString().trimEnd()));
  child.stderr.on('data', (d) => log('warning', d.toString().trimEnd()));
  child.on('exit', (code) => {
    log('info', `DSH 进程退出，exitCode=${code}`);
    if (dshProc === child) {
      dshProc = null;
      const wasStopping = state.serviceState === 'stopping';
      state.serviceState = 'stopped';
      state.stage = null;
      if (!wasStopping) handleAutoRestart(); // 意外退出才触发自动重启
    }
  });
  return true;
}

function stopDsh() {
  return new Promise((resolve) => {
    const p = dshProc;
    if (!p) { resolve(true); return; }
    state.serviceState = 'stopping';
    log('info', '发送终止信号，等待 DSH 优雅退出…');
    p.kill('SIGTERM');
    const timer = setTimeout(() => {
      log('warning', 'DSH 未在超时内退出，强制结束');
      p.kill('SIGKILL');
      resolve(false);
    }, 5000);
    p.once('exit', () => { clearTimeout(timer); resolve(true); });
  });
}

function handleAutoRestart() {
  if (!settings.autoRestart) return;
  const now = Date.now();
  if (now - restartGuard.last > 30000) restartGuard.consecutive = 0;
  restartGuard.consecutive++;
  restartGuard.last = now;
  if (restartGuard.consecutive < 2) {
    log('warning', '服务意外退出，尝试自动重启');
    startSequence();
  } else {
    log('error', '服务反复崩溃，已停止自动重启，请查看日志');
  }
}

// ---- 一键启动时序（对齐 PRD 3.4）----
async function startSequence() {
  if (state.serviceState === 'running' || state.serviceState === 'starting') return;
  if (state.environmentState !== 'ready') {
    log('error', '环境未就绪，无法启动');
    state.serviceState = 'failed';
    return;
  }
  if (!loadKey()) {
    log('error', '未填写 API Key，无法启动');
    state.serviceState = 'failed';
    return;
  }
  state.serviceState = 'starting';
  state.stage = '正在启动容器';
  startDsh();
  state.stage = '正在等待服务';
  const ready = await waitForPort(DSH_PORT);
  if (!ready) {
    log('error', `端口探测超时（60s），服务未就绪`);
    state.serviceState = 'failed';
    state.stage = null;
    await stopDsh();
    return;
  }
  state.serviceState = 'running';
  state.stage = null;
  restartGuard = { consecutive: 0, last: 0 };
  log('info', `小肥鱼运行中，端口 ${DSH_PORT}`);
}

// ---- 环境准备（PC 演示：模拟进度，无 RootFS 解压）----
function prepareEnvironment() {
  if (state.environmentState === 'preparing') return;
  state.environmentState = 'preparing';
  state.progress = 0;
  log('info', '开始准备运行环境（PC 版：校验 Node 与 DSH 命令）');
  let p = 0;
  const timer = setInterval(() => {
    p += 0.1;
    state.progress = Math.min(p, 1);
    if (p >= 1) {
      clearInterval(timer);
      state.environmentState = 'ready';
      state.progress = 1;
      log('info', '环境就绪（DSH 端口 ' + DSH_PORT + '）');
    }
  }, 150);
}

function resetEnvironment() {
  state.environmentState = 'uninitialized';
  state.progress = 0;
  clearLogs();
  log('info', '运行环境已重置');
}

// ---- 设置（对齐 SettingsRepository）----
const settings = {
  autoRestart: false,
  keepAlive: true,
  notifications: true,
};

// ---- HTTP 服务 ----
const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
};

function sendJson(res, code, obj) {
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(obj));
}

function readBody(req) {
  return new Promise((resolve) => {
    let data = '';
    req.on('data', (c) => (data += c));
    req.on('end', () => {
      try { resolve(JSON.parse(data || '{}')); } catch { resolve({}); }
    });
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://127.0.0.1:${LAUNCHER_PORT}`);
  const route = url.pathname;
  const method = req.method;

  try {
    // ---- API ----
    if (route === '/api/state') {
      return sendJson(res, 200, {
        environmentState: state.environmentState,
        serviceState: state.serviceState,
        stage: state.stage,
        progress: state.progress,
        port: state.port,
        maskedKey: maskedKey(loadKey()),
        settings,
      });
    }
    if (route === '/api/prepare' && method === 'POST') { prepareEnvironment(); return sendJson(res, 200, { ok: true }); }
    if (route === '/api/start' && method === 'POST') { startSequence(); return sendJson(res, 200, { ok: true }); }
    if (route === '/api/stop' && method === 'POST') { await stopDsh(); return sendJson(res, 200, { ok: true }); }
    if (route === '/api/reset' && method === 'POST') { resetEnvironment(); return sendJson(res, 200, { ok: true }); }

    if (route === '/api/logs') {
      return sendJson(res, 200, { logs });
    }
    if (route === '/api/logs/clear' && method === 'POST') { clearLogs(); return sendJson(res, 200, { ok: true }); }
    if (route === '/api/logs/export') {
      const text = logs.map((l) => `[${l.level}] ${l.message}`).join('\n');
      res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8', 'Content-Disposition': 'attachment; filename="fish_log.txt"' });
      return res.end(text);
    }

    if (route === '/api/key') {
      if (method === 'GET') return sendJson(res, 200, { maskedKey: maskedKey(loadKey()) });
      if (method === 'POST') { const b = await readBody(req); saveKey(b.key || ''); log('info', 'API Key 已更新'); return sendJson(res, 200, { ok: true }); }
      if (method === 'DELETE') { clearKey(); log('info', 'API Key 已清除'); return sendJson(res, 200, { ok: true }); }
    }

    if (route === '/api/settings') {
      if (method === 'GET') return sendJson(res, 200, { settings });
      if (method === 'POST') { const b = await readBody(req); Object.assign(settings, b.settings || {}); return sendJson(res, 200, { ok: true }); }
    }

    // ---- 静态文件 ----
    let filePath = path.join(PUBLIC_DIR, route === '/' ? 'index.html' : route);
    if (!filePath.startsWith(PUBLIC_DIR)) { return sendJson(res, 403, { error: 'forbidden' }); }
    fs.readFile(filePath, (err, data) => {
      if (err) { res.writeHead(404); return res.end('Not Found'); }
      const ext = path.extname(filePath).toLowerCase();
      res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream' });
      res.end(data);
    });
  } catch (e) {
    sendJson(res, 500, { error: String(e && e.message || e) });
  }
});

server.listen(LAUNCHER_PORT, () => {
  console.log('==============================================');
  console.log('  一键领养小肥鱼 —— PC 版启动器');
  console.log(`  界面地址: http://127.0.0.1:${LAUNCHER_PORT}`);
  console.log(`  DSH 端口: ${DSH_PORT}（可设 DSH_PORT 环境变量）`);
  console.log(`  DSH 命令: ${DSH_COMMAND}`);
  console.log('==============================================');
});
