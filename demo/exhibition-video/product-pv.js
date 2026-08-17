const stage = document.querySelector('#motion-stage');
document.body.classList.add('pv2');
stage.setAttribute('aria-hidden', 'false');
stage.innerHTML = `
<svg viewBox="0 0 1920 1080" role="img" aria-label="PDToscillo 2.5D プロモーション映像">
  <defs>
    <filter id="blur"><feGaussianBlur stdDeviation="18"/></filter>
    <filter id="glow"><feGaussianBlur stdDeviation="5" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#071421"/><stop offset="1" stop-color="#03070e"/></linearGradient>
    <linearGradient id="device" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#435766"/><stop offset="1" stop-color="#182731"/></linearGradient>
    <linearGradient id="pdt" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#e8edf0"/><stop offset=".55" stop-color="#b9c4cb"/><stop offset="1" stop-color="#778792"/></linearGradient>
    <pattern id="grid" width="60" height="60" patternUnits="userSpaceOnUse"><path d="M60 0H0V60" fill="none" stroke="#173849" stroke-width="1"/></pattern>
  </defs>
  <rect width="1920" height="1080" fill="url(#bg)"/>
  <path id="parallax" d="M0 810L1920 680V1080H0Z" fill="#071725"/><path d="M0 845L1920 735" stroke="#15394c" stroke-width="2"/>
  <g transform="translate(88 58)"><rect width="42" height="42" rx="12" fill="#34c9d5"/><text x="21" y="30" text-anchor="middle" fill="#fff" font-size="26" font-weight="700">P</text><text x="58" y="29" fill="#f3fbff" font-size="27" font-weight="700">PDToscillo</text><text x="260" y="28" class="label cyan" font-size="16">PROMOTION FILM / 02</text></g>
  <text id="clock" x="1818" y="86" text-anchor="end" class="label" fill="#7294a8" font-size="16">00:00</text>

  <g id="scene1" class="scene">
    <text x="100" y="190" class="scene-tag label">SCENE 01 / LEGACY WORKFLOW</text>
    <text x="100" y="280" class="headline"><tspan x="100">専用計測には、</tspan><tspan x="100" dy="82">重いPCと専用ソフト。</tspan></text>
    <text x="104" y="470" class="sub"><tspan x="104">現場へ運び、接続し、起動する。</tspan><tspan x="104" dy="42">それだけで準備は増えていく。</tspan></text>
    <g id="legacy-instrument" transform="translate(1260 340)">
      <ellipse class="shadow" cx="190" cy="420" rx="260" ry="42"/>
      <path d="M0 40L55 0H410L455 42V405H0Z" fill="url(#device)" stroke="#526a77" stroke-width="3"/>
      <rect x="45" y="82" width="250" height="178" rx="10" class="glass"/><text x="69" y="120" class="label cyan" font-size="18">SPECIAL ANALYZER</text>
      <path d="M68 207C108 158 132 232 175 170S236 222 274 154" class="wave"/>
      <g fill="#9aadb8"><circle cx="350" cy="105" r="25"/><circle cx="350" cy="174" r="25"/><circle cx="350" cy="243" r="25"/></g>
      <rect x="66" y="315" width="92" height="40" rx="6" fill="#182934" stroke="#758c98"/><text x="112" y="341" text-anchor="middle" class="label" fill="#aebfc7" font-size="14">RS-232</text>
    </g>
    <path id="legacy-cable" d="M1318 688C1130 748 1020 738 904 690" class="cable-muted"/>
    <g id="heavy-worker" transform="translate(790 410)">
      <ellipse class="shadow" cx="0" cy="420" rx="160" ry="32"/>
      <g id="worker-body"><circle class="skin" cx="0" cy="32" r="52"/><path class="suit" d="M-82 105Q0 68 82 105L65 285H-65Z"/><path class="worker-line" d="M-52 142L-135 245L-204 268"/><path class="worker-line" d="M53 145L122 225L185 250"/><path class="worker-line" d="M-34 278L-62 416"/><path class="worker-line" d="M34 278L76 416"/></g>
      <g id="heavy-pc" transform="translate(-270 232)"><rect width="330" height="175" rx="14" fill="#273641" stroke="#738692" stroke-width="4"/><path d="M70 0V-28Q70-54 98-54H232Q260-54 260-28V0" fill="none" stroke="#748895" stroke-width="16"/><text x="165" y="80" text-anchor="middle" class="label" fill="#d2e0e6" font-size="18">FIELD PC</text><text x="165" y="119" text-anchor="middle" fill="#ffb45d" font-size="26" font-weight="700">HEAVY</text></g>
    </g>
    <g id="legacy-window" transform="translate(710 170)">
      <rect width="520" height="315" rx="18" fill="#dfe5e8"/><rect width="520" height="54" rx="18" fill="#385061"/><circle cx="32" cy="27" r="8" fill="#ff657a"/><circle cx="58" cy="27" r="8" fill="#ffc35b"/>
      <text x="28" y="92" fill="#263743" font-size="18" font-weight="700">Measurement Suite 2009</text><rect x="28" y="116" width="464" height="160" rx="10" fill="#fff"/>
      <g id="os-error"><circle cx="83" cy="193" r="34" fill="#ff657a"/><text x="83" y="205" text-anchor="middle" fill="#fff" font-size="38" font-weight="700">!</text><text x="137" y="173" fill="#263743" font-size="22" font-weight="700">このソフトウェアは</text><text x="137" y="210" fill="#263743" font-size="22" font-weight="700">現在のOSに対応していません</text><rect x="350" y="230" width="110" height="30" rx="6" fill="#607887"/><text x="405" y="251" text-anchor="middle" fill="#fff" font-size="15">閉じる</text></g>
    </g>
  </g>

  <g id="scene2" class="scene">
    <text x="100" y="190" class="scene-tag label">SCENE 02 / BUILT-IN DISPLAY</text>
    <text x="100" y="280" class="headline"><tspan x="100">画面を付ければ、</tspan><tspan x="100" dy="82">それで解決？</tspan></text>
    <text x="104" y="470" class="sub"><tspan x="104">高価。重い。ボタンもメニューも多い。</tspan><tspan x="104" dy="42">初見では、測定までたどり着けない。</tspan></text>
    <g id="big-instrument" transform="translate(1040 245)">
      <ellipse class="shadow" cx="355" cy="610" rx="410" ry="46"/><path d="M0 70L90 0H690L770 80V585H0Z" fill="url(#device)" stroke="#657986" stroke-width="5"/>
      <rect x="55" y="115" width="410" height="280" rx="10" class="glass"/><rect x="80" y="142" width="360" height="230" fill="#08131b"/>
      <g id="deep-menu"><rect x="95" y="158" width="112" height="194" fill="#173044"/><g fill="#7e9aab"><rect x="110" y="180" width="78" height="12"/><rect x="110" y="215" width="64" height="12"/><rect x="110" y="250" width="84" height="12"/><rect x="110" y="285" width="56" height="12"/></g><rect x="226" y="171" width="192" height="55" rx="5" fill="#22475b"/><rect x="226" y="240" width="192" height="43" rx="5" fill="#153343"/><rect x="226" y="296" width="192" height="43" rx="5" fill="#153343"/><path d="M240 205H388M240 260H360M240 315H400" stroke="#5e8294" stroke-width="8"/></g>
      <g fill="#a8b7be"><circle cx="550" cy="145" r="31"/><circle cx="640" cy="145" r="31"/><circle cx="550" cy="240" r="31"/><circle cx="640" cy="240" r="31"/><circle cx="550" cy="335" r="31"/><circle cx="640" cy="335" r="31"/></g>
      <g fill="#667d8b"><rect x="518" y="430" width="55" height="32" rx="5"/><rect x="595" y="430" width="55" height="32" rx="5"/><rect x="672" y="430" width="55" height="32" rx="5"/><rect x="518" y="480" width="55" height="32" rx="5"/><rect x="595" y="480" width="55" height="32" rx="5"/><rect x="672" y="480" width="55" height="32" rx="5"/></g>
      <g id="price-tag" transform="translate(520 -35) rotate(7)"><path d="M0 0H205V90H0L-35 45Z" fill="#7b2634" stroke="#ff7c8f" stroke-width="3"/><circle cx="-7" cy="45" r="8" fill="#dbe8ed"/><text x="98" y="37" text-anchor="middle" fill="#ffc7cf" font-size="18" class="label">HIGH COST</text><text x="98" y="68" text-anchor="middle" fill="#fff" font-size="23" font-weight="700">高価</text></g>
      <g id="weight-meter" transform="translate(95 445)"><text y="23" fill="#a8bdc8" font-size="17" class="label">WEIGHT</text><rect y="42" width="330" height="26" rx="13" fill="#172b36"/><rect width="294" height="26" y="42" rx="13" fill="#ffb45d"/><text x="350" y="65" class="orange" font-size="23" font-weight="700">HEAVY</text></g>
    </g>
    <g id="confused-worker" transform="translate(850 475)"><circle class="skin" cx="0" cy="32" r="50"/><path class="suit" d="M-78 106Q0 68 78 106L62 282H-62Z"/><g id="confused-arms"><path class="worker-line" d="M-50 142L-140 90L-190 28"/><path class="worker-line" d="M52 142L142 92L194 34"/></g><path class="worker-line" d="M-30 280L-58 425"/><path class="worker-line" d="M30 280L66 425"/><text x="0" y="-50" text-anchor="middle" fill="#ffb45d" font-size="42" font-weight="700">?</text></g>
  </g>

  <g id="scene3" class="scene">
    <text x="100" y="180" class="scene-tag label">SCENE 03 / PDT-FP1 + LAN</text>
    <text x="100" y="270" class="headline"><tspan x="100">PCを持ち歩かず、</tspan><tspan x="100" dy="82">手元でデータを見る。</tspan></text>
    <text x="104" y="460" class="sub"><tspan x="104">専用計測器とPDT-FP1をLANで接続。</tspan><tspan x="104" dy="42">グラフも、数値も、パラメーターも。</tspan></text>
    <g transform="translate(104 570)"><rect class="badge" width="180" height="45" rx="22"/><text x="90" y="30" text-anchor="middle" class="badge-text">利用コンセプト</text><text x="205" y="31" class="micro">PDT-FP1：約308g</text></g>
    <g id="scene3-instrument" transform="translate(830 405) scale(.72)"><use href="#legacy-instrument" x="-1260" y="-340"/></g>
    <path id="lan-cable" d="M1160 730C1280 800 1370 800 1490 748" class="cable"/>
    <g id="lan-packets" fill="#dffffa"><circle r="10"/><circle r="8"/><circle r="12"/></g>
    <g id="pdt-device" transform="translate(1450 235)">
      <ellipse class="shadow" cx="125" cy="600" rx="170" ry="34"/>
      <path d="M28 24L72 0H285L315 30V535L282 566H65L28 536Z" fill="#6d7d87"/>
      <rect width="265" height="566" rx="34" fill="url(#pdt)" stroke="#f2f5f6" stroke-width="4"/>
      <rect x="15" y="28" width="235" height="470" rx="26" fill="#06131d"/>
      <circle cx="132" cy="14" r="5" fill="#263943"/>
      <g id="pdt-ui">
        <text x="35" y="72" fill="#f2fbff" font-size="24" font-weight="700">PDToscillo</text><text x="35" y="104" class="cyan label" font-size="13">LAN / CONNECTED</text>
        <rect x="32" y="132" width="201" height="160" rx="8" fill="#0b2230"/><path d="M42 245C67 190 82 262 109 205S147 248 171 188S207 240 224 181" class="wave" stroke-width="5"/>
        <text x="38" y="330" fill="#8ba8b8" font-size="14">LIVE VALUE</text><text x="38" y="370" fill="#f3fbff" font-size="32" font-weight="700">24.83</text><text x="145" y="370" class="cyan" font-size="16">mm</text>
        <rect x="34" y="398" width="90" height="55" rx="8" fill="#123247"/><rect x="138" y="398" width="90" height="55" rx="8" fill="#123247"/><text x="79" y="430" text-anchor="middle" fill="#b8d2dd" font-size="13">PARAM A</text><text x="183" y="430" text-anchor="middle" fill="#b8d2dd" font-size="13">EXPORT</text>
      </g>
      <g id="lan-port"><rect x="-13" y="485" width="38" height="54" rx="5" fill="#081117" stroke="#62eddf" stroke-width="3"/><text x="-20" y="473" class="cyan label" font-size="12">LAN</text><path d="M-30 499H-10V526H-30" fill="#536873" stroke="#9fb1b9" stroke-width="2"/></g>
    </g>
    <g id="light-worker" transform="translate(720 560) scale(.72)"><circle class="skin" cx="0" cy="32" r="50"/><path class="suit" d="M-78 106Q0 68 78 106L62 282H-62Z"/><path class="worker-line" d="M-50 142L-120 205"/><path class="worker-line" d="M50 142L146 80L205 45"/><path class="worker-line" d="M-30 280L-58 425"/><path class="worker-line" d="M30 280L66 425"/><path d="M-27 20Q0 40 27 20" fill="none" stroke="#5f3d31" stroke-width="5" stroke-linecap="round"/></g>
  </g>

  <g id="scene4" class="scene">
    <text x="100" y="168" class="scene-tag label">SCENE 04 / FUTURE VISION</text>
    <text x="100" y="250" class="headline">LANを挿すだけで、機器に合うUIへ。</text>
    <g transform="translate(1545 120)"><rect class="badge" width="250" height="48" rx="24"/><text x="125" y="32" text-anchor="middle" class="badge-text">将来ビジョン</text></g>
    <g id="device-cards" transform="translate(90 350)">
      <g class="device-card" data-index="0"><rect width="340" height="180" rx="22" class="panel"/><text x="26" y="45" class="scene-tag label">SPECIAL INSTRUMENT</text><path d="M35 135C70 95 96 152 132 105S196 150 230 91S281 131 310 84" class="wave" stroke-width="5"/><text x="26" y="165" class="micro">グラフ＋パラメーター</text></g>
      <g class="device-card" data-index="1" transform="translate(370)"><rect width="340" height="180" rx="22" class="panel"/><text x="26" y="45" class="scene-tag label">LXI / SCPI</text><path d="M30 112H310M30 82H310M70 65V145M130 65V145M190 65V145M250 65V145" class="screen-grid"/><path d="M30 116C65 50 92 146 129 76S188 138 225 71S282 137 312 73" class="wave" stroke-width="5"/><text x="26" y="165" class="micro">波形＋カーソル</text></g>
      <g class="device-card" data-index="2" transform="translate(740)"><rect width="340" height="180" rx="22" class="panel"/><text x="26" y="45" class="scene-tag label">DANTE / AES67</text><g class="eq-bars" fill="#55e8dc"><rect x="40" y="116" width="20" height="31"/><rect x="76" y="90" width="20" height="57"/><rect x="112" y="64" width="20" height="83"/><rect x="148" y="102" width="20" height="45"/><rect x="184" y="75" width="20" height="72"/><rect x="220" y="52" width="20" height="95"/><rect x="256" y="83" width="20" height="64"/><rect x="292" y="107" width="20" height="40"/></g><text x="26" y="165" class="micro">EQ＋フェーダー</text></g>
      <g class="device-card" data-index="3" transform="translate(1110)"><rect width="340" height="180" rx="22" class="panel"/><text x="26" y="45" class="scene-tag label">GIGE VISION</text><rect x="28" y="62" width="284" height="91" rx="8" fill="#17354a"/><circle cx="100" cy="100" r="27" fill="#ffb45d"/><path d="M28 153L98 98L147 134L210 79L312 153" fill="#23566c"/><path d="M190 67h52M216 55v25" stroke="#fff" stroke-width="3"/><text x="26" y="174" class="micro">カメラプレビュー</text></g>
    </g>
    <path d="M250 650H1670" class="cable"/><g id="future-pulse"><circle r="18" fill="#dffffa" filter="url(#glow)"/></g>
    <g id="recognizer" transform="translate(750 675)"><rect width="420" height="155" rx="24" fill="#102a39" stroke="#55e8dc" stroke-width="3"/><text x="210" y="42" text-anchor="middle" class="scene-tag label">PROTOCOL ADAPTER</text><text id="detect-line" x="210" y="82" text-anchor="middle" fill="#f0fbff" font-size="23" font-weight="700">識別応答を解析中...</text><text x="210" y="119" text-anchor="middle" class="micro">service info / device response / signature</text></g>
    <g id="future-ui" transform="translate(620 860)"><rect width="680" height="125" rx="22" fill="#081722" stroke="#35586b" stroke-width="2"/><text x="36" y="42" class="label cyan" font-size="16">AUTO UI SWITCH</text><text id="ui-title" x="36" y="88" fill="#f2fbff" font-size="31" font-weight="700">グラフとパラメーターを表示</text><rect x="570" y="34" width="76" height="58" rx="12" fill="#103a42"/><path d="M588 72l16 16 29-39" fill="none" stroke="#55e8dc" stroke-width="7"/></g>
    <text x="1810" y="1030" text-anchor="end" class="micro">※ 自動判別・汎用UI切替は将来構想です。</text>
  </g>

  <rect id="wipe" class="cut" width="1920" height="1080"/>
  <g transform="translate(88 1010)"><rect width="1744" height="3" fill="#193a4a"/><rect id="progress" width="0" height="3" fill="#55e8dc"/></g>
</svg>`;

const $ = id => stage.querySelector(id);
const scenes = ['#scene1','#scene2','#scene3','#scene4'].map($);
const clamp = v => Math.max(0, Math.min(1, v));
const smooth = v => { v=clamp(v); return v*v*(3-2*v); };
function setTransform(selector, value) { $(selector)?.setAttribute('transform', value); }
function renderAt(raw) {
  const t = Math.min(Math.max(raw, 0), 59.999);
  const index = t < 15 ? 0 : t < 28 ? 1 : t < 44 ? 2 : 3;
  scenes.forEach((s,i) => s.style.opacity = i === index ? '1' : '0');
  $('#progress').setAttribute('width', String(1744 * t / 60));
  $('#clock').textContent = `00:${String(Math.floor(t)).padStart(2,'0')}`;
  $('#parallax').setAttribute('transform', `translate(${-20*Math.sin(t*.18)} 0)`);
  const local = index === 0 ? t : index === 1 ? t-15 : index === 2 ? t-28 : t-44;
  if (index === 0) {
    const carry = smooth(local/5), strain = Math.sin(local*3)*4;
    setTransform('#heavy-worker', `translate(${790-125*(1-carry)} ${410+strain}) rotate(${-8+8*carry} 0 250)`);
    setTransform('#heavy-pc', `translate(${-270+75*carry} ${232+35*carry}) rotate(${-8+8*carry})`);
    $('#legacy-window').style.opacity = String(clamp((local-5)/1.4));
    $('#os-error').style.opacity = String(local > 7 ? .7+.3*Math.abs(Math.sin(local*3)) : 0);
    $('#legacy-cable').style.strokeDasharray = '18 13'; $('#legacy-cable').style.strokeDashoffset = String(-local*18);
  } else if (index === 1) {
    setTransform('#big-instrument', `translate(${1040+12*Math.sin(local*.7)} 245)`);
    setTransform('#confused-arms', `rotate(${6*Math.sin(local*2.5)} 0 140)`);
    setTransform('#price-tag', `translate(520 -35) rotate(${7+4*Math.sin(local*2)})`);
    $('#deep-menu').style.opacity = String(.65+.35*Math.abs(Math.sin(local*1.7)));
  } else if (index === 2) {
    const plug = smooth(clamp((local-2)/4));
    $('#lan-cable').style.strokeDasharray = '22 16'; $('#lan-cable').style.strokeDashoffset = String(-local*32);
    setTransform('#pdt-device', `translate(${1500-50*plug} ${235-8*Math.sin(local*.7)}) rotate(${3-3*plug} 130 280)`);
    $('#pdt-ui').style.opacity = String(clamp((local-6)/2));
    $('#lan-packets').style.opacity = String(local > 4 ? 1 : 0);
    const points = [0,.33,.67]; $('#lan-packets').querySelectorAll('circle').forEach((c,i)=>{const p=(local*.22+points[i])%1; const x=1160+(1490-1160)*p, y=730+70*Math.sin(Math.PI*p); c.setAttribute('cx',x);c.setAttribute('cy',y);});
    setTransform('#light-worker', `translate(720 ${560-8*Math.sin(local*.8)}) scale(.72)`);
  } else {
    const active = Math.min(3, Math.floor(local/4));
    const labels=['専用計測器を識別','LXI / SCPI を識別','Dante / AES67 を識別','GigE Vision を識別'];
    const ui=['グラフとパラメーターを表示','波形とカーソルUIへ切替','イコライザーとフェーダーへ切替','ライブプレビューへ切替'];
    $('#detect-line').textContent = labels[active]; $('#ui-title').textContent = ui[active];
    stage.querySelectorAll('.device-card').forEach((c,i)=>{c.style.opacity=i===active?'1':'.28';c.style.filter=i===active?'url(#glow)':'none';});
    stage.querySelectorAll('.eq-bars rect').forEach((b,i)=>b.setAttribute('height',String(35+45*Math.abs(Math.sin(local*2+i)))));
    setTransform('#future-pulse', `translate(${250+1420*((local*.18)%1)} 650)`);
    setTransform('#recognizer', `translate(750 ${675+4*Math.sin(local*2)})`);
  }
  const nearest = [15,28,44].reduce((a,b)=>Math.abs(t-b)<Math.abs(t-a)?b:a,15);
  const d = Math.abs(t-nearest); const wipe = d < .42 ? 1-d/.42 : 0;
  $('#wipe').style.opacity=String(wipe*.92); $('#wipe').setAttribute('x',String((1-wipe)*1920));
}
window.__renderFrame = renderAt; window.__duration = 60; window.__variant = 'product-pv';
renderAt(0);
if (!new URLSearchParams(location.search).has('capture')) {
  const tick = ms => { renderAt((ms/1000)%60); requestAnimationFrame(tick); }; requestAnimationFrame(tick);
}
