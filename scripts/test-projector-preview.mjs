/**
 * Dependency-free browser smoke test. Requires Node 22+ and Chromium.
 * node scripts/test-projector-preview.mjs
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
  ['/', ['index.html', 'text/html']], ['/index.html', ['index.html', 'text/html']],
  ['/visualizer.js', ['visualizer.js', 'text/javascript']], ['/visualizer.css', ['visualizer.css', 'text/css']]
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
  '--use-angle=swiftshader', '--enable-unsafe-swiftshader',
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
async function pixels() {
  // WebGL's default drawing buffer is discarded after compositing; toDataURL()
  // outside the render callback can be blank. Capture the composited frame.
  return (await send('Page.captureScreenshot', { format: 'png' })).data;
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
  assert.equal(await evaluate('document.getElementById("error").hidden'), true, 'Shader initializes');
  assert.match(await evaluate('document.getElementById("status").textContent'), /Sin audio/);
  await evaluate('document.getElementById("demo").click()'); await frames();
  assert.match(await evaluate('document.getElementById("status").textContent'), /sintético/);
  await screenshot('materia-desktop.png');
  const initial = await pixels();
  await frames();
  assert.ok(await pixels() !== initial, 'Scene actually animates');
  await evaluate('document.querySelector(\'[data-palette="1"]\').click()'); await frames();
  assert.equal(await evaluate('document.querySelector(\'[data-palette="1"]\').getAttribute("aria-pressed")'), 'true');
  await screenshot('materia-amber.png');
  await evaluate('document.getElementById("intensity").value=1.2; document.getElementById("intensity").dispatchEvent(new Event("input"))');
  assert.equal(await evaluate('document.getElementById("intensity-value").value'), '1.20');
  await evaluate('document.getElementById("pause").click()');
  assert.equal(await evaluate('document.getElementById("pause").getAttribute("aria-pressed")'), 'true');
  await evaluate('document.getElementById("pause").click()');
  await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true });
  await frames();
  assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true, 'No mobile horizontal overflow');
  assert.equal(await evaluate('document.querySelector(".console").getBoundingClientRect().bottom < innerHeight'), true);
  await screenshot('materia-mobile.png');
  await send('Emulation.setDeviceMetricsOverride', { width: 1240, height: 920, deviceScaleFactor: 1, mobile: false });
  await navigate(`${url}/?embedded=1`);
  assert.equal(await evaluate('getComputedStyle(document.getElementById("controls")).display'), 'none', 'Projector has no preview UI');
  await evaluate('window.setProjectorActive(true);window.setProjectorAudio(0.8,0.5,0.2)'); await frames();
  await screenshot('materia-projection.png');
  await evaluate('window.setProjectorActive(false)'); await frames();
  const suspended = await pixels();
  await frames();
  assert.ok(await pixels() === suspended, 'Host can suspend rendering');
  await evaluate('window.setProjectorActive(true)'); await frames();
  assert.ok(await pixels() !== suspended, 'Host can resume rendering');
  await evaluate('window.setProjectorAudio(NaN,Infinity,-2)'); await frames();
  if (artifactDir && process.env.PROJECTOR_RECORD === '1') {
    const video = await evaluate(`new Promise((resolve,reject) => {
      document.getElementById('demo').click();
      const stream = document.getElementById('scene').captureStream(20);
      const recorder = new MediaRecorder(stream, {mimeType:'video/webm'});
      const chunks = [];
      recorder.ondataavailable = e => { if(e.data.size) chunks.push(e.data); };
      recorder.onerror = () => reject(new Error('Video recording failed'));
      recorder.onstop = () => {
        stream.getTracks().forEach(t => t.stop());
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result.split(',')[1]);
        reader.readAsDataURL(new Blob(chunks,{type:'video/webm'}));
      };
      recorder.start();
      setTimeout(() => recorder.stop(),6000);
    })`);
    await writeFile(path.join(artifactDir, 'materia-demo.webm'), Buffer.from(video, 'base64'));
  }
  assert.equal(errors.length, 0, JSON.stringify(errors));
  console.log('PASS: shader, animation, palettes, intensity, pause control, mobile layout, embedded mode, native features and suspend/resume');
  if (artifactDir) console.log(`Rendered screenshots: ${artifactDir}`);
} finally {
  socket?.close();
  for (const call of pending.values()) clearTimeout(call.timeout);
  chrome.kill();
  await new Promise(resolve => server.close(resolve));
  // Chromium may still be flushing its private profile after SIGTERM.
  await rm(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
}
