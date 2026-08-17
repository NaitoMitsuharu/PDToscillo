import { createServer } from 'node:http';
import { createRequire } from 'node:module';
import { existsSync, mkdtempSync, rmSync } from 'node:fs';
import { readFile, stat } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { basename, dirname, extname, join, normalize, resolve } from 'node:path';
import { spawn } from 'node:child_process';

const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const root = dirname(new URL(import.meta.url).pathname.replace(/^\/(.:)/, '$1'));
const fps = 15;
const duration = 45;
const output = join(root, 'PDToscillo-exhibition.mp4');
const defaultFfmpeg = join(process.env.LOCALAPPDATA, 'Microsoft', 'WinGet', 'Packages', 'Gyan.FFmpeg.Essentials_Microsoft.Winget.Source_8wekyb3d8bbwe', 'ffmpeg-8.1.1-essentials_build', 'bin', 'ffmpeg.exe');
const ffmpeg = process.env.FFMPEG_PATH || defaultFfmpeg;
if (!existsSync(ffmpeg)) throw new Error(`FFmpeg が見つかりません: ${ffmpeg}`);
const mime = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8' };
const server = createServer(async (req, res) => {
  const requested = req.url?.split('?')[0] || '/index.html';
  if (requested.includes('..')) { res.writeHead(403).end(); return; }
  const path = resolve(root, `.${normalize(requested)}`);
  try { const file = await readFile(path); await stat(path); res.writeHead(200, { 'content-type': mime[extname(path)] || 'application/octet-stream' }); res.end(file); } catch { console.error(`not found: ${requested} -> ${path}`); res.writeHead(404).end(); }
});
await new Promise(resolveServer => server.listen(0, '127.0.0.1', resolveServer));
const port = server.address().port;
const frames = mkdtempSync(join(tmpdir(), 'pdtoscillo-three-'));
function run(command, args) { return new Promise((resolveRun, reject) => { const child = spawn(command, args, { stdio: 'inherit' }); child.on('error', reject); child.on('exit', code => code === 0 ? resolveRun() : reject(new Error(`${basename(command)} failed: ${code}`))); }); }
try {
  const browser = await chromium.launch({ headless: true, args: ['--use-angle=swiftshader', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage({ viewport: { width: 1280, height: 720 }, deviceScaleFactor: 1 });
  page.on('console', message => console.error(`browser: ${message.type()} ${message.text()}`));
  page.on('pageerror', error => console.error(`browser error: ${error.stack || error.message}`));
  await page.goto(`http://127.0.0.1:${port}/index.html?capture=1`, { waitUntil: 'networkidle' });
  await page.waitForFunction(() => typeof window.__renderFrame === 'function');
  await page.evaluate(() => document.fonts.ready);
  for (let frame = 0; frame < duration * fps; frame += 1) {
    await page.evaluate(time => window.__renderFrame(time), frame / fps);
    await page.screenshot({ path: join(frames, `frame-${String(frame).padStart(4, '0')}.png`) });
  }
  await browser.close();
  await run(ffmpeg, ['-y', '-framerate', String(fps), '-i', join(frames, 'frame-%04d.png'), '-vf', 'fps=30,scale=1920:1080:flags=lanczos', '-c:v', 'libx264', '-preset', 'medium', '-crf', '20', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', output]);
  console.log(output);
} finally { server.close(); rmSync(frames, { recursive: true, force: true }); }
