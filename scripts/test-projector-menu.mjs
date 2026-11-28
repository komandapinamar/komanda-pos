/**
 * Dependency-free browser smoke test for the menu preview.
 * Requires Node 22+ and Chromium.
 * node scripts/test-projector-menu.mjs
 * Optional PROJECTOR_ARTIFACTS=/absolute/path saves actual rendered screenshots.
 */
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { mkdtemp, readFile, mkdir, writeFile, rm } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const assets = fileURLToPath(new URL('../app/src/main/assets/projector/', import.meta.url));
const profile = await mkdtemp('/tmp/opencode/projector-chrome-');
const artifactDir = process.env.PROJECTOR_ARTIFACTS;
const allowedFiles = new Map([
  ['/', ['menu.html', 'text/html']],
  ['/menu.html', ['menu.html', 'text/html']],
  ['/menu.css', ['menu.css', 'text/css']],
  ['/menu.js', ['menu.js', 'text/javascript']]
]);
const server = createServer(async (request, response) => {
  const file = allowedFiles.get(new URL(request.url, 'http://localhost').pathname);
  if (!file) { response.writeHead(404).end(); return; }
  try { response.setHeader('Content-Type', file[1]); response.end(await readFile(path.join(assets, file[0]))); }
  catch { response.writeHead(500).end(); }
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const url = `http://127.0.0.1:${server.address().port}`;
const chrome = spawn(process.env.CHROMIUM || 'chromium', [
  '--headless=new', '--no-sandbox', '--disable-dev-shm-usage', '--no-first-run',
  '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank'
], { stdio: ['ignore', 'ignore', 'pipe'] });
let socket;
let sequence = 0;
const pending = new Map();
const events = new Map();
const errors = [];
function event(name) {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error(`Timed out: ${name}`)), 30000);
    events.set(name, data => { clearTimeout(timeout); resolve(data); });
  });
}
function send(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = ++sequence;
    const timeout = setTimeout(() => { pending.delete(id); reject(new Error(`Timed out: ${method}`)); }, 30000);
    pending.set(id, { resolve, reject, timeout });
    socket.send(JSON.stringify({ id, method, params }));
  });
}
async function evaluate(expression) {
  const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  return result.result.value;
}
async function frames() {
  await evaluate('new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(() => requestAnimationFrame(resolve))))');
}
async function navigate(destination) {
  const loaded = event('Page.loadEventFired');
  await send('Page.navigate', { url: destination }); await loaded; await frames();
}
async function screenshot(name) {
  if (!artifactDir) return;
  const image = await send('Page.captureScreenshot', { format: 'png' });
  await writeFile(path.join(artifactDir, name), Buffer.from(image.data, 'base64'));
}
try {
  const endpoint = await new Promise((resolve, reject) => {
    let output = '';
    const timeout = setTimeout(() => reject(new Error('Chromium did not start')), 30000);
    chrome.once('error', reject);
    chrome.once('exit', code => { clearTimeout(timeout); reject(new Error(`Chromium exited: ${code}`)); });
    chrome.stderr.on('data', chunk => {
      output += chunk;
      const match = output.match(/DevTools listening on (ws:\/\/[^\s]+)/);
      if (match) { clearTimeout(timeout); resolve(match[1]); }
    });
  });
  const port = new URL(endpoint).port;
  const tab = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: 'PUT' })).json();
  socket = new WebSocket(tab.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, { once: true }); socket.addEventListener('error', reject, { once: true }); });
  socket.addEventListener('message', message => {
    const data = JSON.parse(message.data);
    if (data.id) {
      const call = pending.get(data.id);
      if (!call) return;
      clearTimeout(call.timeout); pending.delete(data.id);
      if (data.error) call.reject(new Error(JSON.stringify(data.error))); else call.resolve(data.result);
    } else {
      if (data.method === 'Runtime.exceptionThrown') errors.push(data.params.exceptionDetails);
      const callback = events.get(data.method);
      if (callback) { events.delete(data.method); callback(data.params); }
    }
  });
  await send('Page.enable'); await send('Runtime.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: 1600, height: 1000, deviceScaleFactor: 1, mobile: false });
  if (artifactDir) await mkdir(artifactDir, { recursive: true });
  await navigate(url);

  assert.match(await evaluate('document.title'), /Vista previa/, 'Menu preview page');
  assert.equal(await evaluate('document.querySelector("canvas")'), null, 'No canvas or WebGL scene');
  assert.equal(await evaluate('document.querySelector("audio")'), null, 'No media element');
  assert.equal(await evaluate('document.getElementById("demo")'), null, 'No demo control');
  assert.equal(await evaluate('document.getElementById("microphone")'), null, 'No microphone control');
  assert.equal(await evaluate('document.querySelector(".spectrum")'), null, 'No meters');
  assert.equal(await evaluate('document.querySelectorAll("[data-palette]").length'), 3);
  assert.match(await evaluate('document.getElementById("status").textContent'), /Vista previa/);

  await screenshot('menu-desktop.png');
  await evaluate('document.querySelector(\'[data-palette="1"]\').click()');
  assert.equal(await evaluate('document.querySelector(\'[data-palette="1"]\').getAttribute("aria-pressed")'), 'true');
  await evaluate('document.getElementById("intensity").value=1.2; document.getElementById("intensity").dispatchEvent(new Event("input"))');
  assert.equal(await evaluate('document.getElementById("intensity-value").value'), '1.20');
  assert.equal(await evaluate('document.querySelectorAll(".actions button").length'), 1, 'Only fullscreen action');

  await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true });
  await frames();
  assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true, 'No mobile horizontal overflow');
  assert.equal(await evaluate('document.querySelector(".console").getBoundingClientRect().bottom < innerHeight'), true);
  await screenshot('menu-mobile.png');

  assert.equal(errors.length, 0, JSON.stringify(errors));
  console.log('PASS: menu preview layout, palettes, intensity, fullscreen action, mobile layout and no WebGL');
  if (artifactDir) console.log(`Rendered screenshots: ${artifactDir}`);
} finally {
  socket?.close();
  for (const call of pending.values()) clearTimeout(call.timeout);
  chrome.kill();
  await new Promise(resolve => server.close(resolve));
  await rm(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
}
