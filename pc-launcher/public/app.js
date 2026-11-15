// 一键领养小肥鱼 —— PC 版前端逻辑
'use strict';

let state = {
  environmentState: 'uninitialized',
  serviceState: 'stopped',
  stage: null,
  progress: 0,
  port: 51146,
  maskedKey: null,
  settings: { autoRestart: false, keepAlive: true, notifications: true },
};
let logsCache = [];
let logsPaused = false;
let currentScreen = 'welcome';
let chatOpened = false;

// ---- 基础工具 ----
async function api(path, opts) {
  const res = await fetch(path, opts);
  return res.json();
}

function show(name) {
  currentScreen = name;
  document.querySelectorAll('.screen').forEach((s) => s.classList.remove('active'));
  const el = document.getElementById('screen-' + name);
  if (el) el.classList.add('active');
  if (name === 'onboarding') renderOnboarding();
  if (name === 'settings') renderSettings();
}

function showDialog(text, actions) {
  document.getElementById('dialog-text').textContent = text;
  const box = document.getElementById('dialog-actions');
  box.innerHTML = '';
  actions.forEach((a) => {
    const b = document.createElement('button');
    b.textContent = a.label;
    if (a.primary) b.className = 'primary';
    b.onclick = () => { hideDialog(); a.onClick && a.onClick(); };
    box.appendChild(b);
  });
  document.getElementById('dialog').classList.remove('hidden');
}
function hideDialog() { document.getElementById('dialog').classList.add('hidden'); }

// ---- 引导 ----
function finishWelcome() { show('onboarding'); }
function finishOnboarding() { show('home'); }

function renderOnboarding() {
  const items = [
    { t: '运行环境目录', w: 'PC 版在工作目录存放运行数据与密钥文件。', ok: true },
    { t: '日志与通知', w: '运行状态与实时日志在控制页展示。', ok: true },
    { t: '后台运行', w: 'PC 版进程随启动器服务持续运行。', ok: true },
  ];
  const list = document.getElementById('onboarding-list');
  list.innerHTML = items.map((it) => `
    <div class="check-item">
      <div><div>${it.t}</div><div class="why">${it.w}</div></div>
      <span class="badge ${it.ok ? 'ok' : 'miss'}">${it.ok ? '已就绪' : '待检查'}</span>
    </div>`).join('');
}

// ---- 主控制 ----
function mainAction() {
  if (state.serviceState === 'running') { api('/api/stop', { method: 'POST' }); return; }
  if (state.environmentState === 'uninitialized' || state.environmentState === 'corrupted') {
    api('/api/prepare', { method: 'POST' });
  } else {
    api('/api/start', { method: 'POST' });
  }
}

function renderHome() {
  const btn = document.getElementById('main-btn');
  const status = document.getElementById('status-line');
  const prog = document.getElementById('progress-wrap');
  const bar = document.getElementById('progress-bar');

  const env = state.environmentState;
  const svc = state.serviceState;
  let text = '准备环境', disabled = false;

  if (env === 'preparing') { text = state.stage || '正在准备环境'; disabled = true; }
  else if (svc === 'starting') { text = state.stage || '正在启动'; disabled = true; }
  else if (svc === 'running') { text = '停止小肥鱼'; }
  else if (env === 'uninitialized' || env === 'corrupted') { text = '准备环境'; }
  else { text = '一键领养小肥鱼'; }

  btn.textContent = text;
  btn.disabled = disabled;

  let statusText = '环境未准备';
  if (env === 'preparing') statusText = '正在准备环境 ' + Math.round(state.progress * 100) + '%';
  else if (svc === 'starting') statusText = state.stage || '正在启动';
  else if (svc === 'running') statusText = '小肥鱼运行中（端口 ' + state.port + '）';
  else if (env === 'corrupted') statusText = '环境异常，请重新准备';
  else if (env === 'ready') statusText = '就绪';
  status.textContent = statusText;

  if (env === 'preparing') {
    prog.classList.remove('hidden');
    bar.style.width = Math.round(state.progress * 100) + '%';
  } else {
    prog.classList.add('hidden');
  }

  // 就绪后自动切入对话界面
  if (svc === 'running' && !chatOpened) {
    chatOpened = true;
    openChat();
  }
  if (svc !== 'running') chatOpened = false;
}

// ---- 对话界面 ----
function openChat() {
  document.getElementById('chat-frame').src = 'http://127.0.0.1:' + state.port;
  show('chat');
}

function confirmBack() {
  showDialog('小肥鱼仍在后台运行。', [
    { label: '返回控制页', primary: true, onClick: () => show('home') },
    { label: '留在当前页', onClick: () => {} },
  ]);
}

// ---- 日志 ----
function renderLogs() {
  const list = document.getElementById('log-list');
  list.innerHTML = logsCache.map((l) => {
    const cls = 'log-' + (l.level === 'error' ? 'error' : l.level === 'warning' ? 'warning' : 'info');
    return `<div class="log-line ${cls}">${escapeHtml('[' + l.level + '] ' + l.message)}</div>`;
  }).join('');
  if (!logsPaused) list.scrollTop = list.scrollHeight;
}

function escapeHtml(s) {
  return s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
}

function togglePause() {
  logsPaused = !logsPaused;
  document.getElementById('pause-btn').textContent = logsPaused ? '继续' : '暂停';
}

function clearLogs() {
  api('/api/logs/clear', { method: 'POST' }).then(() => { logsCache = []; renderLogs(); });
  setMsg('已清空');
}

function copyLogs() {
  const text = logsCache.map((l) => '[' + l.level + '] ' + l.message).join('\n');
  navigator.clipboard.writeText(text).then(() => setMsg('已复制到剪贴板'));
}

function exportLogs() {
  window.open('/api/logs/export', '_blank');
  setMsg('已导出日志文件');
}

function setMsg(m) {
  const el = document.getElementById('log-msg');
  el.textContent = m;
  setTimeout(() => { el.textContent = ''; }, 3000);
}

// ---- 设置 ----
function renderSettings() {
  const kd = document.getElementById('key-display');
  kd.textContent = state.maskedKey ? '当前密钥：' + state.maskedKey : '尚未填写 API Key';
  document.getElementById('s-auto').checked = !!state.settings.autoRestart;
  document.getElementById('s-keep').checked = !!state.settings.keepAlive;
  document.getElementById('s-notif').checked = !!state.settings.notifications;
}

function saveKey() {
  const v = document.getElementById('key-input').value.trim();
  if (!v) { setMsg('请输入 API Key'); return; }
  api('/api/key', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ key: v }) })
    .then(() => { document.getElementById('key-input').value = ''; refreshState(); });
}

function confirmClearKey() {
  showDialog('将删除本机保存的 API Key，需重新输入后才能启动。', [
    { label: '确认清除', primary: true, onClick: () => api('/api/key', { method: 'DELETE' }).then(refreshState) },
    { label: '取消', onClick: () => {} },
  ]);
}

function confirmReset() {
  showDialog('将删除运行环境内的全部数据，回到刚安装状态。', [
    { label: '确认重置', primary: true, onClick: () => api('/api/reset', { method: 'POST' }).then(refreshState) },
    { label: '取消', onClick: () => {} },
  ]);
}

function bindSettings() {
  const post = (s) => api('/api/settings', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ settings: s }) });
  document.getElementById('s-auto').addEventListener('change', (e) => post({ autoRestart: e.target.checked }));
  document.getElementById('s-keep').addEventListener('change', (e) => post({ keepAlive: e.target.checked }));
  document.getElementById('s-notif').addEventListener('change', (e) => post({ notifications: e.target.checked }));
}

// ---- 轮询 ----
async function refreshState() {
  state = await api('/api/state');
  renderHome();
  if (currentScreen === 'settings') renderSettings();
}

async function refreshLogs() {
  const r = await api('/api/logs');
  logsCache = r.logs || [];
  renderLogs();
}

async function poll() {
  await refreshState();
  await refreshLogs();
}

// ---- 启动 ----
bindSettings();
show('welcome');
poll();
setInterval(poll, 1000);
