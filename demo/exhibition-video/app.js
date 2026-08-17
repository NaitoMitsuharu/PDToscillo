const demo = document.querySelector('#demo');
const caption = document.querySelector('.caption');
const progress = document.querySelector('.progress-track i');
const wavePaths = document.querySelectorAll('.wave-a');
const waveB = document.querySelector('.wave-b');

const timeline = [
  { at: 0, scene: 'problem', text: '高機能なオシロスコープ。その操作は、初めての人には少し難しい。' },
  { at: 7, scene: 'intro', text: 'PDToscillo ─ LAN対応の計測機器を、手元の画面へ。' },
  { at: 11, scene: 'connect', text: 'LANケーブル1本で、オシロスコープとタブレットを接続。' },
  { at: 18, scene: 'discover', text: '接続された機器を自動で判別し、対応する画面を表示。' },
  { at: 26, scene: 'waveform', text: 'オシロスコープでは、波形を取得して詳しく確認。' },
  { at: 33, scene: 'measure', text: '測定値を、その場で確認。' },
  { at: 39, scene: 'automate', text: '取得・測定・保存の繰り返しも、自動化。' },
  { at: 43, scene: 'outro', text: 'PDToscillo ─ さまざまな計測機器を、ひとつの体験へ。' },
];
const duration = 45;
let start = performance.now();
let active = -1;
let paused = false;
let pauseAt = 0;

function createWave(phase, amplitude = 55, offset = 116) {
  const points = [];
  for (let x = 0; x <= 560; x += 4) {
    const y = offset + Math.sin((x / 560) * Math.PI * 8 + phase) * amplitude
      + Math.sin((x / 560) * Math.PI * 24 + phase) * 5;
    points.push(`${x === 0 ? 'M' : 'L'} ${x} ${y.toFixed(1)}`);
  }
  return points.join(' ');
}
function setScene(index) {
  if (index === active) return;
  active = index;
  demo.className = `stage-${timeline[index].scene}`;
  caption.classList.remove('show');
  caption.textContent = timeline[index].text;
  requestAnimationFrame(() => caption.classList.add('show'));
}
function animate(now) {
  if (paused) return;
  let elapsed = (now - start) / 1000;
  if (elapsed >= duration) { start = now; elapsed = 0; active = -1; }
  let index = timeline.findLastIndex(item => elapsed >= item.at);
  setScene(index);
  progress.style.width = `${(elapsed / duration) * 100}%`;
  const phase = elapsed * 5;
  wavePaths.forEach((path, i) => path.setAttribute('d', createWave(phase + i * .35, i ? 28 : 54, i ? 52 : 116)));
  if (waveB) waveB.setAttribute('d', createWave(phase * .8 + 2, 25, 116));
  requestAnimationFrame(animate);
}
function restart() { start = performance.now(); active = -1; paused = false; requestAnimationFrame(animate); }
document.addEventListener('keydown', event => {
  if (event.key.toLowerCase() === 'r') restart();
  if (event.code === 'Space') {
    event.preventDefault(); paused = !paused;
    if (!paused) { start = performance.now() - pauseAt; requestAnimationFrame(animate); }
    else pauseAt = performance.now() - start;
  }
});
caption.textContent = timeline[0].text;
caption.classList.add('show');
requestAnimationFrame(animate);
