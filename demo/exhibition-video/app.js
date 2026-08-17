import * as THREE from './vendor/three.module.js';

const canvas = document.querySelector('#three-canvas');
const copy = document.querySelector('#copy');
const eyebrow = document.querySelector('#eyebrow');
const headline = document.querySelector('#headline');
const description = document.querySelector('#description');
const disclaimer = document.querySelector('#disclaimer');
const deviceStatus = document.querySelector('#device-status');
const progress = document.querySelector('#timeline i');
const duration = 45;
const captureMode = new URLSearchParams(location.search).has('capture');

const renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
renderer.setSize(1920, 1080, false);
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.12;
const scene = new THREE.Scene();
scene.background = new THREE.Color(0x050d1b);
scene.fog = new THREE.FogExp2(0x050d1b, 0.065);
const camera = new THREE.PerspectiveCamera(37, 16 / 9, 0.1, 100);
camera.position.set(0, 4.5, 14);
const lookTarget = new THREE.Vector3(0, 0.3, 0);
scene.add(new THREE.HemisphereLight(0x90e9ff, 0x07111d, 2.1));
const keyLight = new THREE.DirectionalLight(0x9cd8ff, 2.4); keyLight.position.set(-6, 8, 8); scene.add(keyLight);
const cyanLight = new THREE.PointLight(0x2be1d4, 18, 20, 2); cyanLight.position.set(0, 1, 3); scene.add(cyanLight);
const floor = new THREE.Mesh(new THREE.PlaneGeometry(38, 24), new THREE.MeshStandardMaterial({ color: 0x071a2a, roughness: .5, metalness: .35 }));
floor.rotation.x = -Math.PI / 2; floor.position.y = -2.3; scene.add(floor);
const grid = new THREE.GridHelper(36, 36, 0x1f6a86, 0x15425b); grid.position.y = -2.28; grid.material.transparent = true; grid.material.opacity = .46; scene.add(grid);

function createCanvasTexture(width, height, draw) {
  const el = document.createElement('canvas'); el.width = width; el.height = height;
  const ctx = el.getContext('2d'); draw(ctx, width, height, 0);
  const texture = new THREE.CanvasTexture(el); texture.colorSpace = THREE.SRGBColorSpace;
  return { el, ctx, texture, draw };
}
function drawWave(ctx, w, h, phase, label, details) {
  ctx.fillStyle = '#041420'; ctx.fillRect(0, 0, w, h);
  ctx.strokeStyle = '#123f4e'; ctx.lineWidth = 1;
  for (let x = 0; x <= w; x += w / 10) { ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, h); ctx.stroke(); }
  for (let y = 0; y <= h; y += h / 8) { ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke(); }
  ctx.strokeStyle = '#54e1e0'; ctx.shadowColor = '#36f0e5'; ctx.shadowBlur = 12; ctx.lineWidth = 4; ctx.beginPath();
  for (let x = 0; x <= w; x += 3) { const y = h * .5 + Math.sin(x / w * Math.PI * 12 + phase) * h * .24 + Math.sin(x / w * Math.PI * 35 + phase) * h * .028; x ? ctx.lineTo(x, y) : ctx.moveTo(x, y); } ctx.stroke(); ctx.shadowBlur = 0;
  ctx.fillStyle = '#d8f9ff'; ctx.font = 'bold 20px sans-serif'; ctx.fillText(label, 22, 31); ctx.fillStyle = '#8eb9c8'; ctx.font = '16px monospace'; ctx.fillText(details, 22, h - 19);
}
const scopeDisplay = createCanvasTexture(820, 420, (ctx, w, h, t) => drawWave(ctx, w, h, t, 'CH1  500mV', '1.000 kHz  ·  2.00 Vp-p'));
const tabletDisplay = createCanvasTexture(900, 570, (ctx, w, h, t) => {
  ctx.fillStyle = '#07182a'; ctx.fillRect(0, 0, w, h); ctx.fillStyle = '#eaf8ff'; ctx.font = 'bold 28px sans-serif'; ctx.fillText('PDToscillo', 34, 49); ctx.fillStyle = '#3fd7c8'; ctx.font = '18px monospace'; ctx.fillText('LAN DEVICE DISCOVERY', 34, 80);
  drawWave(ctx, w - 68, 225, t, 'WAVEFORM / CH1', 'AUTO SCALE · CURSOR · EXPORT');
  ctx.fillStyle = '#122d43'; ctx.fillRect(34, 337, 390, 174); ctx.fillRect(448, 337, 418, 174); ctx.fillStyle = '#a8c4d3'; ctx.font = '18px sans-serif'; ctx.fillText('DETECTED', 57, 369); ctx.fillText('MEASUREMENT', 471, 369);
  ctx.fillStyle = '#edfaff'; ctx.font = 'bold 26px sans-serif'; ctx.fillText('MDO4104C', 57, 408); ctx.fillText('1.000 kHz', 471, 408); ctx.fillStyle = '#55e3d1'; ctx.font = '17px sans-serif'; ctx.fillText('Oscilloscope  ·  Connected', 57, 444); ctx.fillText('P-P  2.00 V  ·  Δt 500 μs', 471, 444);
});
function boxGroup(w, h, d, color, metal = .45) { const g = new THREE.Group(); g.add(new THREE.Mesh(new THREE.BoxGeometry(w, h, d), new THREE.MeshStandardMaterial({ color, metalness: metal, roughness: .3 }))); return g; }
function screenMesh(texture, w, h) { const m = new THREE.Mesh(new THREE.PlaneGeometry(w, h), new THREE.MeshBasicMaterial({ map: texture })); m.position.z = .061; return m; }
const scope = boxGroup(5.2, 3.3, 1.4, 0x2e4054); scope.position.set(-4.8, -.45, 0); scene.add(scope);
const bezel = new THREE.Mesh(new THREE.BoxGeometry(3.35, 2.25, .1), new THREE.MeshStandardMaterial({ color: 0x0a151e, metalness: .45, roughness: .2 })); bezel.position.set(-.55, .18, .75); scope.add(bezel);
const scopeScreen = screenMesh(scopeDisplay.texture, 3.05, 1.92); scopeScreen.position.set(-.55, .18, .82); scope.add(scopeScreen);
for (let i = 0; i < 4; i++) { const knob = new THREE.Mesh(new THREE.CylinderGeometry(.22, .22, .13, 20), new THREE.MeshStandardMaterial({ color: 0x9aabb8, metalness: .8, roughness: .2 })); knob.rotation.x = Math.PI / 2; knob.position.set(1.65, .85 - i * .62, .78); scope.add(knob); }
const tablet = boxGroup(5.1, 3.25, .32, 0x111d2a, .75); tablet.position.set(1.65, -.15, 1.1); tablet.rotation.x = -.14; scene.add(tablet);
const tabletScreen = screenMesh(tabletDisplay.texture, 4.75, 3.0); tabletScreen.position.set(0, 0, .19); tablet.add(tabletScreen);
const fp1 = boxGroup(2.3, 1.65, 1.35, 0x32485a); fp1.position.set(5.25, -.9, -.25); scene.add(fp1);
const fpLED = new THREE.Mesh(new THREE.SphereGeometry(.1, 16, 16), new THREE.MeshBasicMaterial({ color: 0x45f2b4 })); fpLED.position.set(-.75, .36, .71); fp1.add(fpLED);
const cablePoints = [new THREE.Vector3(-2.25, -1.15, .9), new THREE.Vector3(-.6, -1.8, .55), new THREE.Vector3(1.15, -1.15, 1.27), new THREE.Vector3(4.0, -1.42, .85), new THREE.Vector3(4.35, -1.0, .3)];
const cableCurve = new THREE.CatmullRomCurve3(cablePoints); const cable = new THREE.Line(new THREE.BufferGeometry().setFromPoints(cableCurve.getPoints(80)), new THREE.LineBasicMaterial({ color: 0x47e9d9, transparent: true, opacity: .78 })); scene.add(cable);
const pulses = []; for (let i = 0; i < 5; i++) { const pulse = new THREE.Mesh(new THREE.SphereGeometry(.09, 12, 12), new THREE.MeshBasicMaterial({ color: 0xc8fffb })); scene.add(pulse); pulses.push(pulse); }
const copyStates = [[0, 'THE CHALLENGE', '高機能な計測機器。', '設定、操作、記録。初めて触れる人には、少し複雑です。', ''], [7, 'PDTOSCILLO', 'つなぐ。見る。測る。', 'LAN対応の計測機器を、手元の一つの画面へ。', ''], [13, 'LAN CONNECTION', 'LANで接続。', 'オシロスコープとタブレットを、一本のケーブルで。', ''], [19, 'AUTO DISCOVERY', 'つないだ機器を、\n自動で判別。', '機器に合わせた表示と操作へ、迷わず進めます。', '※ PDT-FP1を含む対応拡張は将来構想です。'], [29, 'OSCILLOSCOPE', '波形を、\nすぐに詳しく。', '取得、拡大、カーソル測定。必要な情報を、すばやく確認。', ''], [37, 'ONE EXPERIENCE', 'さまざまな計測を、\nひとつの体験へ。', 'PDToscillo — 測定の現場を、もっと軽やかに。', '']];
let active = -1;
function updateCopy(t) { const idx = copyStates.findLastIndex(s => t >= s[0]); if (idx === active) return; active = idx; const s = copyStates[idx]; const apply = () => { eyebrow.textContent = s[1]; headline.textContent = s[2]; description.textContent = s[3]; disclaimer.textContent = s[4]; copy.style.opacity = 1; }; if (captureMode) apply(); else { copy.style.opacity = 0; setTimeout(apply, 90); } }
function ease(a, b, t) { return a + (b - a) * (t * t * (3 - 2 * t)); } function clamp01(v) { return Math.max(0, Math.min(1, v)); }
function renderAt(t) { t = Math.min(Math.max(t, 0), duration - .001); updateCopy(t); progress.style.width = `${t / duration * 100}%`; const phase = t * 5; scopeDisplay.draw(scopeDisplay.ctx, 820, 420, phase); scopeDisplay.texture.needsUpdate = true; tabletDisplay.draw(tabletDisplay.ctx, 900, 570, phase); tabletDisplay.texture.needsUpdate = true;
  const discover = clamp01((t - 13) / 7); const measure = clamp01((t - 29) / 7); const finale = clamp01((t - 37) / 8); deviceStatus.textContent = t < 13 ? 'STANDBY' : t < 19 ? 'CONNECTING' : t < 29 ? '2 DEVICES FOUND' : t < 37 ? 'MEASURING' : 'READY';
  scope.position.x = ease(-5.1, -4.8, clamp01((t - 7) / 5)); scope.rotation.y = Math.sin(t * .3) * .035; tablet.position.y = ease(2.4, -.15, clamp01((t - 7) / 5)); tablet.rotation.z = Math.sin(t * .35) * .025; fp1.position.x = ease(8.2, 5.25, discover); fp1.rotation.y = -discover * .18; cable.material.opacity = .15 + discover * .75; fpLED.material.color.setHSL(.43, .9, .45 + discover * .18);
  pulses.forEach((p, i) => { p.position.copy(cableCurve.getPoint((t * .32 + i / 5) % 1)); p.visible = t > 12; }); camera.position.set(ease(1.3, .2, finale), ease(4.8, 3.35, measure), ease(15, 13.2, finale)); lookTarget.set(ease(-.7, .6, finale), ease(.1, -.15, measure), 0); camera.lookAt(lookTarget); renderer.render(scene, camera);
}
function tick(ms) { renderAt((ms / 1000) % duration); if (!captureMode) requestAnimationFrame(tick); }
window.__renderFrame = seconds => renderAt(seconds); window.__duration = duration; renderAt(0); if (!captureMode) requestAnimationFrame(tick);
