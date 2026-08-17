import * as THREE from './vendor/three.module.js';

const params = new URLSearchParams(location.search);
const variant = params.get('variant') || 'product-pv';
const captureMode = params.has('capture');
const duration = 60;
const themes = {
  'product-pv': { name: 'PRODUCT PV', bg: 0x030817, floor: 0x06182b, cyan: 0x42e9db, accent: 0x4f8cff, text: '#edf7ff', muted: '#a9bdd2', camera: 1.25 },
  explainer: { name: 'EXPLAINER', bg: 0xe9f4f5, floor: 0xc9e6e8, cyan: 0x007d84, accent: 0x276fd2, text: '#112a3b', muted: '#405d6c', camera: .82 },
  'technical-demo': { name: 'TECHNICAL DEMO', bg: 0x07100d, floor: 0x0b221d, cyan: 0x5ef076, accent: 0xf1be45, text: '#edfff2', muted: '#a6cbb2', camera: .98 },
};
const theme = themes[variant] || themes['product-pv'];
document.documentElement.dataset.theme = variant;
document.documentElement.style.setProperty('--ink', theme.text);
document.documentElement.style.setProperty('--muted', theme.muted);
document.documentElement.style.setProperty('--cyan', `#${theme.cyan.toString(16).padStart(6, '0')}`);
document.querySelector('.tag').textContent = theme.name;

const $ = s => document.querySelector(s);
const canvas = $('#three-canvas'), copy = $('#copy'), eyebrow = $('#eyebrow'), headline = $('#headline');
const description = $('#description'), disclaimer = $('#disclaimer'), deviceStatus = $('#device-status'), progress = $('#timeline i');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
renderer.setPixelRatio(Math.min(devicePixelRatio, 2)); renderer.setSize(1920, 1080, false); renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.toneMappingExposure = variant === 'explainer' ? 1.28 : 1.08;
const scene = new THREE.Scene(); scene.background = new THREE.Color(theme.bg); scene.fog = new THREE.FogExp2(theme.bg, .045);
const camera = new THREE.PerspectiveCamera(38, 16 / 9, .1, 100), target = new THREE.Vector3();
scene.add(new THREE.HemisphereLight(0xb8f5ff, theme.bg, 2.2));
const key = new THREE.DirectionalLight(0xd5f1ff, 2.8); key.position.set(-7, 9, 8); scene.add(key);
const glow = new THREE.PointLight(theme.cyan, 22, 25, 2); glow.position.set(0, 2, 2); scene.add(glow);
const floor = new THREE.Mesh(new THREE.PlaneGeometry(46, 30), new THREE.MeshStandardMaterial({ color: theme.floor, roughness: .48, metalness: .3 })); floor.rotation.x = -Math.PI / 2; floor.position.y = -2.45; scene.add(floor);
const grid = new THREE.GridHelper(44, 44, theme.cyan, theme.accent); grid.position.y = -2.43; grid.material.transparent = true; grid.material.opacity = variant === 'explainer' ? .2 : .42; scene.add(grid);

function canvasTexture(w, h, draw) { const el = document.createElement('canvas'); el.width = w; el.height = h; const ctx = el.getContext('2d'); const texture = new THREE.CanvasTexture(el); texture.colorSpace = THREE.SRGBColorSpace; return { ctx, texture, update(t) { draw(ctx, w, h, t); texture.needsUpdate = true; } }; }
function wave(ctx, w, h, t, title) {
  ctx.fillStyle = '#061420'; ctx.fillRect(0, 0, w, h); ctx.strokeStyle = '#1c4b58'; ctx.lineWidth = 1;
  for (let x = 0; x <= w; x += w / 10) { ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, h); ctx.stroke(); }
  for (let y = 0; y <= h; y += h / 7) { ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke(); }
  ctx.strokeStyle = '#55ece0'; ctx.shadowColor = '#32e6d8'; ctx.shadowBlur = 13; ctx.lineWidth = 4; ctx.beginPath();
  for (let x = 0; x <= w; x += 3) { const y = h * .5 + Math.sin(x / w * Math.PI * 11 + t * 5) * h * .22 + Math.sin(x / w * Math.PI * 37 + t * 7) * h * .028; x ? ctx.lineTo(x, y) : ctx.moveTo(x, y); } ctx.stroke(); ctx.shadowBlur = 0;
  ctx.fillStyle = '#e7fbff'; ctx.font = `bold ${Math.round(h / 13)}px sans-serif`; ctx.fillText(title, 20, 30); ctx.fillStyle = '#8db7c5'; ctx.font = `${Math.round(h / 18)}px monospace`; ctx.fillText('CH1  500mV   1.000kHz   2.00Vp-p', 20, h - 17);
}
const scopeTex = canvasTexture(800, 420, (c,w,h,t) => wave(c,w,h,t,'LIVE WAVEFORM'));
const appTex = canvasTexture(900, 570, (c,w,h,t) => { c.fillStyle='#07192a'; c.fillRect(0,0,w,h); c.fillStyle='#ecfbff'; c.font='bold 30px sans-serif'; c.fillText('PDToscillo',34,46); c.fillStyle='#49dfd1'; c.font='18px monospace'; c.fillText('CONNECTED  /  MDO4104C',34,78); wave(c,w-68,225,t,'WAVEFORM / CH1'); c.fillStyle='#12334b'; c.fillRect(34,337,390,174); c.fillRect(448,337,418,174); c.fillStyle='#9bc5d5'; c.font='18px sans-serif'; c.fillText('MEASURE',58,370); c.fillText('ACTION',472,370); c.fillStyle='#effcff'; c.font='bold 28px sans-serif'; c.fillText('1.000 kHz',58,412); c.fillText(t < 34 ? 'CURSOR' : t < 39 ? 'EXPORT CSV' : 'AUTO SAVE',472,412); c.fillStyle='#53e8d8'; c.font='17px sans-serif'; c.fillText('P-P 2.00 V  /  Δt 500 μs',58,450); c.fillText('one touch workflow',472,450); });
function groupBox(w,h,d,color) { const g=new THREE.Group(); g.add(new THREE.Mesh(new THREE.BoxGeometry(w,h,d),new THREE.MeshStandardMaterial({color,metalness:.45,roughness:.3}))); return g; }
function screen(tex,w,h) { const m=new THREE.Mesh(new THREE.PlaneGeometry(w,h),new THREE.MeshBasicMaterial({map:tex})); m.position.z=.071; return m; }
function label(text, color = theme.cyan) { const tex=canvasTexture(640,120,(c,w,h)=>{c.clearRect(0,0,w,h);c.fillStyle='#071724dd';c.fillRect(0,0,w,h);c.strokeStyle=`#${color.toString(16).padStart(6,'0')}`;c.lineWidth=4;c.strokeRect(2,2,w-4,h-4);c.fillStyle='#f4ffff';c.font='bold 44px sans-serif';c.fillText(text,28,76);}); tex.update(0); const m=new THREE.Mesh(new THREE.PlaneGeometry(2.85,.53),new THREE.MeshBasicMaterial({map:tex.texture,transparent:true})); return m; }

const pc=groupBox(3.4,2.25,.32,0x1c2c3d); pc.position.set(-7.4,.3,-1.5); const pcScreen=screen(scopeTex.texture,3.1,1.78);pcScreen.position.z=.19;pc.add(pcScreen); const pcBase=new THREE.Mesh(new THREE.BoxGeometry(3.5,.12,2.25),new THREE.MeshStandardMaterial({color:0x304557,metalness:.5,roughness:.35})); pcBase.position.set(0,-1.18,-.96); pc.add(pcBase); scene.add(pc);
const serial=new THREE.Line(new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(-6,-1.1,-1.5),new THREE.Vector3(-3.2,-1.9,-1.25),new THREE.Vector3(-1.7,-1.15,-.6)]),new THREE.LineBasicMaterial({color:0xffa94d,transparent:true})); scene.add(serial);
const scope=groupBox(5.3,3.3,1.4,0x32475c); scope.position.set(-2.2,-.5,0); const bezel=new THREE.Mesh(new THREE.BoxGeometry(3.35,2.25,.1),new THREE.MeshStandardMaterial({color:0x091620,metalness:.4,roughness:.2})); bezel.position.set(-.55,.18,.75); scope.add(bezel); const scopeScreen=screen(scopeTex.texture,3.05,1.92); scopeScreen.position.set(-.55,.18,.82); scope.add(scopeScreen); for(let i=0;i<4;i++){const k=new THREE.Mesh(new THREE.CylinderGeometry(.22,.22,.13,20),new THREE.MeshStandardMaterial({color:0xa6b8c4,metalness:.8,roughness:.2}));k.rotation.x=Math.PI/2;k.position.set(1.64,.86-i*.62,.78);scope.add(k);} scene.add(scope);
const fp1=groupBox(2.35,1.6,1.3,0x3a5162); fp1.position.set(1.4,-.96,-.4); const port=new THREE.Mesh(new THREE.BoxGeometry(.55,.28,.08),new THREE.MeshBasicMaterial({color:0x071319})); port.position.set(0,.1,.69); fp1.add(port); const led=new THREE.Mesh(new THREE.SphereGeometry(.09,16,16),new THREE.MeshBasicMaterial({color:theme.cyan}));led.position.set(-.8,.38,.7);fp1.add(led); scene.add(fp1);
const tablet=groupBox(5.2,3.3,.32,0x101d2a); tablet.position.set(4.6,-.05,.8);tablet.rotation.x=-.14;const tabletScreen=screen(appTex.texture,4.78,3.02);tabletScreen.position.z=.19;tablet.add(tabletScreen);scene.add(tablet);
const cableCurve=new THREE.CatmullRomCurve3([new THREE.Vector3(-.1,-1.12,.85),new THREE.Vector3(.35,-1.8,.45),new THREE.Vector3(1.2,-1.12,.25),new THREE.Vector3(2.2,-1.3,.7),new THREE.Vector3(2.2,-.5,.9)]); const cable=new THREE.Line(new THREE.BufferGeometry().setFromPoints(cableCurve.getPoints(80)),new THREE.LineBasicMaterial({color:theme.cyan,transparent:true,opacity:.8}));scene.add(cable);
const pulses=Array.from({length:7},()=>{const p=new THREE.Mesh(new THREE.SphereGeometry(.09,12,12),new THREE.MeshBasicMaterial({color:0xe6fffc}));scene.add(p);return p;});
const future=new THREE.Group(); scene.add(future); const futureSpecs=[['LXI / SCPI','DMM'],['LXI / SCPI','DAQ'],['DANTE / AES67','MIXER'],['GIGE VISION','CAMERA']]; futureSpecs.forEach(([a,b],i)=>{const g=groupBox(1.5,1.15,.65,[0x44637b,0x4d6972,0x725a82,0x456a62][i]);g.position.set(2.2+(i%2)*3.0,-.55,-3.8+Math.floor(i/2)*2.5);const l=label(`${a}  ${b}`,i===2?theme.accent:theme.cyan);l.scale.set(.48,.48,.48);l.position.set(0,.98,.35);g.add(l);future.add(g);}); const core=new THREE.Mesh(new THREE.SphereGeometry(.7,32,32),new THREE.MeshStandardMaterial({color:theme.cyan,emissive:theme.cyan,emissiveIntensity:.55,metalness:.5,roughness:.25}));core.position.set(4.5,.1,-2.4);future.add(core);future.visible=false;
const story=[[0,'THE PROBLEM','計測は、PCと専用ソフト。','RS-232やLANの設定、持ち運ぶPC。現場の準備に手間がかかる。',''],[11,'THE PROBLEM','画面付き計測器は、重く、高価。','現場に欲しい表示と操作を機器側へ持たせるほど、開発も機材も大きくなる。',''],[20,'THE PROPOSAL','PDT-FP1を、LANの窓口に。','持ち歩ける端末とLAN接続で、計測機器の情報を手元へ。',''],[29,'WHAT IT CAN DO','波形を見る。測る。操作する。','オシロスコープの取得、拡大、カーソル測定、CSV保存、自動化を一つの画面で。','現行デモ：Tektronix 4000系／SCPI Raw Socket'],[41,'HOW IT WORKS','接続先を識別し、表示を組み立てる。','LAN接続 → 機器識別 → プロトコルアダプター → 機器に合った操作画面。','現行対応はSCPI Raw Socket。汎用自動識別は将来構想です。'],[51,'THE FUTURE','LANを挿すだけで、機器に合う体験へ。','LXI/SCPI計測器、Dante音響機器、GigE Visionカメラ。規格別アダプターで広げていく。','※ 将来ビジョン：対応規格・機種は段階的に拡張予定']];let active=-1; const clamp=v=>Math.max(0,Math.min(1,v)); const smooth=v=>v*v*(3-2*v); const mix=(a,b,t)=>a+(b-a)*smooth(clamp(t));
function setCopy(t){const i=story.findLastIndex(s=>t>=s[0]);if(i===active)return;active=i;const s=story[i];eyebrow.textContent=s[1];headline.textContent=s[2];description.textContent=s[3];disclaimer.textContent=s[4];copy.classList.remove('changed');void copy.offsetWidth;copy.classList.add('changed');}
function renderAt(raw){const t=Math.min(Math.max(raw,0),duration-.001);setCopy(t);progress.style.width=`${t/duration*100}%`;scopeTex.update(t);appTex.update(t);const proposal=clamp((t-18)/8), demo=clamp((t-28)/8), mechanism=clamp((t-40)/7), finale=clamp((t-50)/8);pc.position.x=mix(-7.6,-10.3,proposal);pc.rotation.y=Math.sin(t*.8)*.09*(1-proposal);serial.material.opacity=(1-proposal)*.9;scope.position.x=mix(-2.2,-3.4,finale);scope.rotation.y=Math.sin(t*.45)*.035;fp1.position.set(mix(9,1.4,proposal),-.96,mix(1.5,-.4,proposal));fp1.rotation.y=-proposal*.22;tablet.position.set(mix(9,4.6,demo),mix(2.7,-.05,demo),.8);tablet.rotation.z=Math.sin(t*.45)*.025;cable.material.opacity=proposal*.9;led.material.color.setHex(theme.cyan);pulses.forEach((p,i)=>{p.visible=t>20&&t<51;p.position.copy(cableCurve.getPoint((t*.42+i/pulses.length)%1));});future.visible=t>40;future.position.x=mix(7,0,mechanism);future.position.z=mix(4,0,mechanism);future.rotation.y=(1-mechanism)*-.65;core.scale.setScalar(1+Math.sin(t*4)*.08);grid.rotation.y=t*.012;deviceStatus.textContent=t<20?'SETUP':t<28?'LAN CONNECTED':t<41?'OSCILLOSCOPE / READY':t<51?'IDENTIFYING':'EXPANDABLE';const shot=t<18?0:t<29?1:t<41?2:t<51?3:4;const shots=[[.3,3.4,15,-3.2,.1,0],[1.8,2.6,12,.5,-.45,0],[2.6,2.2,12,1.6,-.2,.2],[3.2,3.1,14,2,.05,-1.5],[2.8,3.5,15,1,.0,-1.3]];const q=shots[shot];camera.position.set(q[0]*theme.camera,q[1],q[2]);target.set(q[3],q[4],q[5]);camera.lookAt(target);renderer.render(scene,camera);}
window.__renderFrame=renderAt;window.__duration=duration;window.__variant=variant;renderAt(0);if(!captureMode){const tick=ms=>{renderAt((ms/1000)%duration);requestAnimationFrame(tick)};requestAnimationFrame(tick);}
