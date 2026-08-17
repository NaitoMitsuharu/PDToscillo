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
    <linearGradient id="pdt-black" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#303338"/><stop offset=".42" stop-color="#111418"/><stop offset="1" stop-color="#050608"/></linearGradient>
    <linearGradient id="pdt-edge" x1="0" y1="0" x2="1" y2="0"><stop stop-color="#07090b"/><stop offset=".55" stop-color="#272b30"/><stop offset="1" stop-color="#080a0c"/></linearGradient>
    <linearGradient id="pdt-glass" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#101b22"/><stop offset="1" stop-color="#020609"/></linearGradient>
    <pattern id="grid" width="60" height="60" patternUnits="userSpaceOnUse"><path d="M60 0H0V60" fill="none" stroke="#173849" stroke-width="1"/></pattern>
    <symbol id="pdt-fp1-shell" viewBox="0 0 330 620">
      <path d="M44 13H286Q311 13 320 39L330 88V542L318 581Q311 606 286 610H48Q18 606 12 578L4 532V84L17 39Q24 13 44 13Z" fill="#050709" stroke="#3a3e42" stroke-width="4"/>
      <path d="M286 25L320 47V565L295 600L286 573Z" fill="url(#pdt-edge)"/>
      <g opacity=".85" stroke="#42474b" stroke-width="3"><path d="M301 84V246"/><path d="M307 84V246"/><path d="M313 84V246"/><path d="M319 84V246"/></g>
      <path d="M33 2H281Q304 2 311 27L318 72V541L307 581Q301 600 279 600H35Q13 600 8 578L0 537V77L10 28Q15 2 33 2Z" fill="url(#pdt-black)" stroke="#55595d" stroke-width="3"/>
      <rect x="25" y="50" width="266" height="500" rx="9" fill="url(#pdt-glass)" stroke="#3c4145" stroke-width="3"/>
      <path d="M298 74L318 84V274L298 265Z" fill="#0b0e11" stroke="#4a4e52" stroke-width="2"/><g stroke="#5a5e62" stroke-width="2"><path d="M302 88V251"/><path d="M307 90V254"/><path d="M312 92V257"/></g>
      <path d="M126 25H203" stroke="#707377" stroke-width="6" stroke-linecap="round"/><circle cx="225" cy="25" r="5" fill="#191d20" stroke="#4d5256" stroke-width="2"/>
      <path d="M5 410H0M5 455H0M312 168H318V242H312" stroke="#73767a" stroke-width="5" stroke-linecap="round"/>
      <path d="M31 600H284L275 616H42Z" fill="#0a0c0f" stroke="#3c4145" stroke-width="3"/>
      <g transform="translate(45 586)"><rect width="67" height="24" rx="3" fill="#030405" stroke="#74787c" stroke-width="2"/><path d="M10 3V18M20 3V18M30 3V18M40 3V18M50 3V18" stroke="#353a3e" stroke-width="2"/><path d="M9 16H58" stroke="#969a9e" stroke-width="2"/></g>
      <g fill="#050607" stroke="#62666a" stroke-width="2"><rect x="131" y="591" width="36" height="12" rx="5"/><rect x="182" y="591" width="36" height="12" rx="5"/><path d="M239 590H285V605H239Z"/></g>
      <g fill="#94999d" font-size="6" font-weight="700" letter-spacing="1"><text x="79" y="582" text-anchor="middle">LAN</text><text x="149" y="582" text-anchor="middle">DATA</text><text x="200" y="582" text-anchor="middle">CHARGE</text><text x="261" y="582" text-anchor="middle">HDMI</text></g>
      <path d="M20 68L31 36H83" fill="none" stroke="#6b6f73" stroke-width="2" opacity=".55"/><path d="M24 566L40 585H90" fill="none" stroke="#6b6f73" stroke-width="2" opacity=".4"/>
    </symbol>
  </defs>
  <rect width="1920" height="1080" fill="url(#bg)"/>
  <path id="parallax" d="M0 810L1920 680V1080H0Z" fill="#071725"/><path d="M0 845L1920 735" stroke="#15394c" stroke-width="2"/>
  <g id="scene1" class="scene">
    <text x="100" y="190" class="scene-tag label">SCENE 01 / LEGACY WORKFLOW</text>
    <text x="100" y="280" class="headline"><tspan x="100">専用計測には、</tspan><tspan x="100" dy="82">重いPCと専用ソフト。</tspan></text>
    <text x="104" y="470" class="sub"><tspan x="104">現場へ運び、接続し、起動する。</tspan><tspan x="104" dy="42">それだけで準備は増えていく。</tspan></text>
    <g id="test-car" transform="translate(1260 265)">
      <ellipse class="shadow" cx="240" cy="260" rx="275" ry="35"/>
      <path d="M30 165L82 96H210L288 37H415L492 104L552 128V211H18Z" fill="#285d78" stroke="#72a3b9" stroke-width="4"/>
      <path d="M238 95L304 52H398L453 105Z" fill="#9dc5d2" opacity=".72"/>
      <circle cx="139" cy="218" r="61" fill="#09131b" stroke="#718590" stroke-width="12"/><circle cx="429" cy="218" r="61" fill="#09131b" stroke="#718590" stroke-width="12"/>
      <circle cx="139" cy="218" r="18" fill="#b8c4ca"/><circle cx="429" cy="218" r="18" fill="#b8c4ca"/>
      <path id="tension-line" d="M25 186C-70 230-63 382 20 455" fill="none" stroke="#ffb45d" stroke-width="12" stroke-linecap="round"/>
      <text x="286" y="18" text-anchor="middle" class="label" fill="#a7c2cf" font-size="16">VEHICLE UNDER TEST</text>
    </g>
    <g id="legacy-instrument" transform="translate(1260 560)">
      <ellipse class="shadow" cx="185" cy="315" rx="230" ry="35"/>
      <path d="M0 35L38 0H390L430 38V292H0Z" fill="url(#device)" stroke="#6f8793" stroke-width="4"/>
      <path d="M38 58H390M38 82H390" stroke="#1b2e39" stroke-width="10" stroke-dasharray="22 12"/>
      <text x="42" y="128" class="label cyan" font-size="18">VEHICLE TENSION TESTER</text>
      <circle cx="20" cy="160" r="15" fill="#09151c" stroke="#ffb45d" stroke-width="5"/><text x="43" y="166" fill="#ffcd8c" font-size="12" class="label">SENSOR IN</text>
      <g transform="translate(52 157)"><circle cx="42" cy="42" r="38" fill="#0a151c" stroke="#ffb45d" stroke-width="5"/><path d="M42 10V74M10 42H74" stroke="#ffb45d" stroke-width="5"/><text x="98" y="35" fill="#b6cad3" font-size="16">LOAD CELL</text><text x="98" y="63" class="micro">NO DISPLAY</text></g>
      <circle cx="365" cy="172" r="11" fill="#55e8dc"/><circle cx="365" cy="210" r="11" fill="#ffb45d"/>
      <rect x="48" y="247" width="92" height="34" rx="5" fill="#142630" stroke="#82949d"/><text x="94" y="270" text-anchor="middle" class="label" fill="#b6c8d0" font-size="12">RS-232</text>
      <rect x="160" y="247" width="92" height="34" rx="5" fill="#142630" stroke="#55e8dc"/><text x="206" y="270" text-anchor="middle" class="label cyan" font-size="12">LAN</text>
    </g>
    <path id="legacy-cable" d="M1420 827C1240 870 1060 790 904 690" class="cable-muted"/>
    <g id="heavy-worker" transform="translate(790 470)">
      <ellipse class="shadow" cx="0" cy="420" rx="160" ry="32"/>
      <g id="worker-body"><circle class="skin" cx="0" cy="32" r="52"/><path class="suit" d="M-82 105Q0 68 82 105L65 285H-65Z"/><path class="worker-line" d="M-52 142L-135 245L-204 268"/><path class="worker-line" d="M53 145L122 225L185 250"/><path class="worker-line" d="M-34 278L-62 416"/><path class="worker-line" d="M34 278L76 416"/></g>
      <g id="heavy-pc" transform="translate(-270 232)"><rect width="330" height="175" rx="14" fill="#273641" stroke="#738692" stroke-width="4"/><path d="M70 0V-28Q70-54 98-54H232Q260-54 260-28V0" fill="none" stroke="#748895" stroke-width="16"/><text x="165" y="80" text-anchor="middle" class="label" fill="#d2e0e6" font-size="18">MEASUREMENT PC</text><text x="165" y="120" text-anchor="middle" fill="#9fb6c2" font-size="18">専用ソフト</text></g>
      <g id="sweat"><path d="M60-40C85-7 76 12 59 12S36-7 60-40Z" fill="#66dcf2"/><path d="M103-15C121 9 116 24 102 24S86 8 103-15Z" fill="#66dcf2"/></g>
      <g id="heavy-speech" transform="translate(110 -25)"><rect x="-10" y="-54" width="150" height="58" rx="26" fill="#fff"/><path d="M28 0L4 25L52 5Z" fill="#fff"/><text x="65" y="-17" text-anchor="middle" fill="#273641" font-size="25" font-weight="700">重い…</text></g>
    </g>
    <g id="legacy-window" transform="translate(690 125) scale(.82)">
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
      <path d="M74 30V2Q74-25 105-25H280Q311-25 311 2V30M470 30V2Q470-25 501-25H657Q688-25 688 2V30" fill="none" stroke="#8396a0" stroke-width="18"/>
      <g fill="#192a34" stroke="#768b96" stroke-width="3"><circle cx="25" cy="94" r="7"/><circle cx="744" cy="105" r="7"/><circle cx="25" cy="548" r="7"/><circle cx="744" cy="548" r="7"/></g>
      <rect x="55" y="115" width="410" height="280" rx="10" class="glass"/><rect x="80" y="142" width="360" height="230" fill="#08131b"/>
      <g id="deep-menu"><rect x="95" y="158" width="112" height="194" fill="#173044"/><g fill="#7e9aab"><rect x="110" y="180" width="78" height="12"/><rect x="110" y="215" width="64" height="12"/><rect x="110" y="250" width="84" height="12"/><rect x="110" y="285" width="56" height="12"/></g><rect x="226" y="171" width="192" height="55" rx="5" fill="#22475b"/><rect x="226" y="240" width="192" height="43" rx="5" fill="#153343"/><rect x="226" y="296" width="192" height="43" rx="5" fill="#153343"/><path d="M240 205H388M240 260H360M240 315H400" stroke="#5e8294" stroke-width="8"/></g>
      <g fill="#a8b7be"><circle cx="550" cy="145" r="31"/><circle cx="640" cy="145" r="31"/><circle cx="550" cy="240" r="31"/><circle cx="640" cy="240" r="31"/><circle cx="550" cy="335" r="31"/><circle cx="640" cy="335" r="31"/></g>
      <g fill="none" stroke="#536d7a" stroke-width="7"><circle cx="550" cy="145" r="43"/><circle cx="640" cy="145" r="43"/><circle cx="550" cy="240" r="43"/><circle cx="640" cy="240" r="43"/><circle cx="550" cy="335" r="43"/><circle cx="640" cy="335" r="43"/></g>
      <g fill="#667d8b"><rect x="518" y="430" width="55" height="32" rx="5"/><rect x="595" y="430" width="55" height="32" rx="5"/><rect x="672" y="430" width="55" height="32" rx="5"/><rect x="518" y="480" width="55" height="32" rx="5"/><rect x="595" y="480" width="55" height="32" rx="5"/><rect x="672" y="480" width="55" height="32" rx="5"/></g>
      <g transform="translate(70 430)"><rect width="380" height="105" rx="8" fill="#1a2c36"/><path d="M18 22H362M18 43H362M18 64H362M18 85H362" stroke="#5a707b" stroke-width="7" stroke-dasharray="15 10"/></g>
      <g fill="#17242c" stroke="#899ba4" stroke-width="4"><path d="M68 573H180V602Q124 618 68 602Z"/><path d="M588 573H700V602Q644 618 588 602Z"/></g>
      <path d="M78 618L38 645M122 616L110 654M642 616L670 651M684 614L730 640" stroke="#49616d" stroke-width="5"/>
      <g id="price-tag" transform="translate(520 -35) rotate(7)"><path d="M0 0H205V90H0L-35 45Z" fill="#7b2634" stroke="#ff7c8f" stroke-width="3"/><circle cx="-7" cy="45" r="8" fill="#dbe8ed"/><text x="98" y="37" text-anchor="middle" fill="#ffc7cf" font-size="18" class="label">HIGH COST</text><text x="98" y="68" text-anchor="middle" fill="#fff" font-size="23" font-weight="700">高価</text></g>
    </g>
    <g id="confused-worker" transform="translate(850 475)"><circle class="skin" cx="0" cy="32" r="50"/><path class="suit" d="M-78 106Q0 68 78 106L62 282H-62Z"/><g id="confused-arms"><path class="worker-line" d="M-50 142L-140 90L-190 28"/><path class="worker-line" d="M52 142L142 92L194 34"/></g><path class="worker-line" d="M-30 280L-58 425"/><path class="worker-line" d="M30 280L66 425"/><text x="0" y="-50" text-anchor="middle" fill="#ffb45d" font-size="42" font-weight="700">?</text></g>
  </g>

  <g id="scene3" class="scene">
    <text x="100" y="180" class="scene-tag label">SCENE 03 / PDT-FP1 + LAN</text>
    <text x="100" y="270" class="headline"><tspan x="100">PCを持ち歩かず、</tspan><tspan x="100" dy="82">手元でデータを見る。</tspan></text>
    <text x="104" y="460" class="sub"><tspan x="104">専用計測器とPDT-FP1をLANで接続。</tspan><tspan x="104" dy="42">グラフも、数値も、パラメーターも。</tspan></text>
    <g transform="translate(104 570)"><rect class="badge" width="180" height="45" rx="22"/><text x="90" y="30" text-anchor="middle" class="badge-text">利用コンセプト</text><text x="205" y="31" class="micro">PDT-FP1：約308g</text></g>
    <g id="scene3-instrument" transform="translate(830 405) scale(.72)"><use href="#legacy-instrument" x="-1260" y="-560"/></g>
    <path id="lan-cable" d="M978 596C1080 770 1325 895 1528 841" class="cable"/>
    <g id="lan-packets" fill="#dffffa"><circle r="10"/><circle r="8"/><circle r="12"/></g>
    <g id="pdt-device" transform="translate(1450 215)">
      <ellipse class="shadow" cx="165" cy="640" rx="185" ry="34"/>
      <use href="#pdt-fp1-shell" width="330" height="620"/>
      <g id="pdt-ui">
        <text x="47" y="92" fill="#f2fbff" font-size="23" font-weight="700">PDToscillo</text><text x="47" y="120" class="cyan label" font-size="12">LAN / CONNECTED</text>
        <rect x="43" y="145" width="230" height="170" rx="8" fill="#0b2230"/><path d="M53 264C78 205 100 280 128 218S170 265 195 202S238 253 263 195" class="wave" stroke-width="5"/>
        <text x="47" y="351" fill="#8ba8b8" font-size="14">LIVE VALUE</text><text x="47" y="394" fill="#f3fbff" font-size="34" font-weight="700">24.83</text><text x="167" y="394" class="cyan" font-size="16">mm</text>
        <rect x="43" y="425" width="105" height="58" rx="8" fill="#123247"/><rect x="164" y="425" width="105" height="58" rx="8" fill="#123247"/><text x="95" y="459" text-anchor="middle" fill="#b8d2dd" font-size="13">PARAM A</text><text x="216" y="459" text-anchor="middle" fill="#b8d2dd" font-size="13">EXPORT</text>
      </g>
      <g id="scene3-lan-plug" transform="translate(78 606)"><path d="M-26 0V26H26V0" fill="#4d616c" stroke="#a9b7bd" stroke-width="3"/><path d="M-17 3V18M-7 3V18M3 3V18M13 3V18" stroke="#d7e0e4" stroke-width="3"/></g>
    </g>
    <g id="pc-retired" transform="translate(565 640)">
      <rect width="225" height="145" rx="14" fill="#263640" stroke="#6c828d" stroke-width="4"/><rect x="24" y="26" width="177" height="75" rx="6" fill="#0b1921"/><text x="112" y="58" text-anchor="middle" fill="#91a8b3" font-size="14">専用ソフト</text><path d="M48 80H176" stroke="#4b6572" stroke-width="8" stroke-dasharray="18 10"/><path d="M80 145V177M145 145V177M55 177H170" stroke="#6c828d" stroke-width="9" stroke-linecap="round"/>
      <path d="M-15 -16L240 188M240 -16L-15 188" stroke="#ff7183" stroke-width="15" stroke-linecap="round"/>
      <rect x="-40" y="205" width="305" height="58" rx="29" fill="#351a24" stroke="#ff7183" stroke-width="3"/><text x="112" y="243" text-anchor="middle" fill="#ffd5db" font-size="23" font-weight="700">PC・専用ソフト不要</text>
    </g>
  </g>

  <g id="scene4" class="scene">
    <text x="100" y="168" class="scene-tag label">SCENE 04 / FUTURE VISION</text>
    <text x="100" y="250" class="headline">LANを挿すだけで、機器に合うUIへ。</text>
    <g transform="translate(1545 120)"><rect class="badge" width="250" height="48" rx="24"/><text x="125" y="32" text-anchor="middle" class="badge-text">将来ビジョン</text></g>
    <text id="future-action" x="960" y="296" text-anchor="middle" fill="#b8d2dd" font-size="20" font-weight="700">車両を引張る → 張力値が上昇</text>
    <rect x="90" y="315" width="790" height="600" rx="30" class="panel"/><rect x="1040" y="315" width="790" height="600" rx="30" class="panel"/>
    <text x="135" y="365" class="label cyan" font-size="17">CONNECTED DEVICE</text><text x="1085" y="365" class="label cyan" font-size="17">PDT-FP1 / AUTO UI</text>
    <g id="future-devices">
      <g class="future-device" data-index="0" transform="translate(145 395)">
        <text x="340" y="40" text-anchor="middle" fill="#f2fbff" font-size="28" font-weight="700">専用・張力検査機器</text>
        <g id="future-test-car"><path d="M80 180L125 122H255L315 75H445L505 129L555 148V218H65Z" fill="#285d78" stroke="#72a3b9" stroke-width="4"/><path d="M335 84H430L478 130H283Z" fill="#9dc5d2" opacity=".72"/><circle cx="175" cy="225" r="48" fill="#08131a" stroke="#6e8490" stroke-width="10"/><circle cx="465" cy="225" r="48" fill="#08131a" stroke="#6e8490" stroke-width="10"/></g>
        <g id="future-pull-arrow" transform="translate(575 180)"><path d="M0 0H72M72 0L45-22M72 0L45 22" fill="none" stroke="#ffb45d" stroke-width="9" stroke-linecap="round" stroke-linejoin="round"/><text x="36" y="47" text-anchor="middle" fill="#ffcd8c" font-size="13" font-weight="700">引張</text></g>
        <path id="future-tension-link" d="M66 190C18 210 12 265 3 310" fill="none" stroke="#ffb45d" stroke-width="10"/>
        <g transform="translate(20 320)"><rect width="640" height="170" rx="18" fill="url(#device)" stroke="#708690" stroke-width="4"/><path d="M35 42H605" stroke="#1a2b35" stroke-width="14" stroke-dasharray="24 12"/><text x="40" y="88" class="label cyan" font-size="18">VEHICLE TENSION TESTER</text><text x="40" y="126" class="micro">LOAD CELL / NO DISPLAY</text><circle id="future-tension-led" cx="555" cy="100" r="17" fill="#55e8dc"/><rect x="470" y="128" width="122" height="28" rx="4" fill="#09161e" stroke="#55e8dc"/><text x="531" y="148" text-anchor="middle" class="label cyan" font-size="11">LAN</text></g>
      </g>
      <g class="future-device" data-index="1" transform="translate(160 405)">
        <text x="325" y="38" text-anchor="middle" fill="#f2fbff" font-size="28" font-weight="700">LXI / SCPI オシロスコープ</text>
        <rect x="20" y="78" width="650" height="430" rx="22" fill="url(#device)" stroke="#718893" stroke-width="5"/><rect x="65" y="125" width="385" height="285" rx="10" fill="#07141c" stroke="#8a9da6" stroke-width="4"/>
        <path d="M82 195H434M82 255H434M82 315H434M140 143V394M220 143V394M300 143V394M380 143V394" class="screen-grid"/><path id="scope-left-wave" d="M82 310C135 180 173 360 229 225S327 350 379 205S420 290 434 250" class="wave" stroke-width="6"/>
        <g fill="#9eafb7" stroke="#526b78" stroke-width="7"><circle cx="525" cy="155" r="38"/><circle cx="610" cy="155" r="38"/><circle cx="525" cy="265" r="38"/><circle cx="610" cy="265" r="38"/></g><g fill="#284253"><rect x="490" y="355" width="72" height="38" rx="6"/><rect x="575" y="355" width="72" height="38" rx="6"/></g><rect x="565" y="438" width="75" height="34" rx="4" fill="#07141c" stroke="#55e8dc" stroke-width="3"/><text x="603" y="461" text-anchor="middle" class="label cyan" font-size="12">LAN</text>
        <g id="scope-probe"><path d="M675 92C650 132 615 186 560 220" fill="none" stroke="#ffb45d" stroke-width="9" stroke-linecap="round"/><path d="M565 218L434 250" stroke="#f6d6a6" stroke-width="12" stroke-linecap="round"/><circle cx="434" cy="250" r="13" fill="#fff1d6" stroke="#ffb45d" stroke-width="5"/><text x="550" y="265" class="label" fill="#ffcd8c" font-size="12">PROBE</text></g>
        <g id="scope-contact-indicator" transform="translate(70 432)"><rect width="176" height="42" rx="21" fill="#17382f" stroke="#55e8dc" stroke-width="3"/><circle cx="24" cy="21" r="8" fill="#55e8dc"/><text x="103" y="27" text-anchor="middle" fill="#dffffa" font-size="14" font-weight="700">PROBE CONTACT</text></g>
      </g>
      <g class="future-device" data-index="2" transform="translate(155 400)">
        <text x="330" y="40" text-anchor="middle" fill="#f2fbff" font-size="28" font-weight="700">Dante / AES67 DJコントローラー</text>
        <path d="M20 112L65 72H640L680 112V482L642 512H58L20 482Z" fill="#151e27" stroke="#7b8f9b" stroke-width="5"/><rect x="42" y="103" width="616" height="376" rx="18" fill="#080e15" stroke="#304653" stroke-width="3"/>
        <g transform="translate(72 126)"><rect width="210" height="63" rx="8" fill="#102c3d" stroke="#55e8dc" stroke-width="2"/><path d="M14 34C34 12 50 50 72 24S111 45 133 21S170 47 196 18" fill="none" stroke="#55e8dc" stroke-width="5"/><text x="15" y="56" class="micro">DECK A · 124 BPM</text></g>
        <g transform="translate(418 126)"><rect width="210" height="63" rx="8" fill="#251737" stroke="#b785ff" stroke-width="2"/><path d="M14 34C34 12 50 50 72 24S111 45 133 21S170 47 196 18" fill="none" stroke="#b785ff" stroke-width="5"/><text x="15" y="56" class="micro">DECK B · 124 BPM</text></g>
        <g id="dj-jog-left" transform="translate(178 300)"><circle r="88" fill="#172531" stroke="#55e8dc" stroke-width="7"/><circle r="61" fill="#0a1118" stroke="#556b78" stroke-width="4"/><path d="M0-52V-22M37-37L16-16M52 0H22M37 37L16 16M0 52V22M-37 37L-16 16M-52 0H-22M-37-37L-16-16" stroke="#9af8ef" stroke-width="5" stroke-linecap="round"/><circle r="12" fill="#55e8dc"/><circle cy="-69" r="9" fill="#fff" stroke="#55e8dc" stroke-width="4"/></g>
        <g id="dj-jog-right" transform="translate(522 300)"><circle r="88" fill="#231b30" stroke="#b785ff" stroke-width="7"/><circle r="61" fill="#0a1118" stroke="#6d5a7c" stroke-width="4"/><path d="M0-52V-22M37-37L16-16M52 0H22M37 37L16 16M0 52V22M-37 37L-16 16M-52 0H-22M-37-37L-16-16" stroke="#d7baff" stroke-width="5" stroke-linecap="round"/><circle r="12" fill="#b785ff"/><circle cy="-69" r="9" fill="#fff" stroke="#b785ff" stroke-width="4"/></g>
        <g transform="translate(298 120)"><rect width="104" height="260" rx="12" fill="#111d25"/><text x="52" y="27" text-anchor="middle" class="micro">MIXER</text><g fill="#9eb1ba"><circle cx="28" cy="58" r="12"/><circle cx="76" cy="58" r="12"/><circle cx="28" cy="94" r="12"/><circle cx="76" cy="94" r="12"/></g><path d="M29 125V218M75 125V218" stroke="#405865" stroke-width="8"/><rect x="18" y="151" width="22" height="40" rx="6" fill="#55e8dc"/><rect x="64" y="174" width="22" height="40" rx="6" fill="#b785ff"/></g>
        <g fill="#ff6280"><rect x="72" y="407" width="32" height="28" rx="6"/><rect x="112" y="407" width="32" height="28" rx="6"/><rect x="152" y="407" width="32" height="28" rx="6"/><rect x="192" y="407" width="32" height="28" rx="6"/></g><g fill="#55e8dc"><rect x="476" y="407" width="32" height="28" rx="6"/><rect x="516" y="407" width="32" height="28" rx="6"/><rect x="556" y="407" width="32" height="28" rx="6"/><rect x="596" y="407" width="32" height="28" rx="6"/></g>
        <path d="M282 427H418" stroke="#536a76" stroke-width="10" stroke-linecap="round"/><g id="dj-crossfader" transform="translate(336 427)"><rect x="-18" y="-15" width="36" height="30" rx="7" fill="#f3fbff" stroke="#55e8dc" stroke-width="3"/></g>
        <text x="72" y="470" class="micro">PERFORMANCE PADS</text><rect x="545" y="450" width="105" height="32" rx="6" fill="#07141c" stroke="#55e8dc" stroke-width="2"/><text x="598" y="472" text-anchor="middle" class="label cyan" font-size="10">DANTE LAN</text>
      </g>
      <g class="future-device" data-index="3" transform="translate(155 400)">
        <text x="330" y="40" text-anchor="middle" fill="#f2fbff" font-size="28" font-weight="700">GigE Vision 産業用カメラ</text>
        <rect x="28" y="86" width="640" height="395" rx="22" fill="#102b3a" stroke="#395f72" stroke-width="4"/><circle cx="550" cy="158" r="45" fill="#ffb45d"/><path d="M28 481L165 265L285 390L400 225L525 365L610 278L668 360V481Z" fill="#245f78"/><path d="M28 481L190 350L300 430L415 342L550 430L668 365V481Z" fill="#173f55"/>
        <g id="world-bird"><path d="M0 12Q15-8 30 12Q45-8 60 12" fill="none" stroke="#f3fbff" stroke-width="7" stroke-linecap="round"/></g>
        <g transform="translate(92 305)"><path d="M0 35L34 0H174L205 35V142H0Z" fill="url(#device)" stroke="#8298a3" stroke-width="5"/><rect x="28" y="56" width="84" height="60" rx="7" fill="#07141c" stroke="#55e8dc" stroke-width="3"/><circle cx="70" cy="86" r="10" fill="#55e8dc"/><path d="M205 58H270L322 78V122L270 140H205Z" fill="#1d303a" stroke="#8298a3" stroke-width="5"/><path d="M322 80L370 67V133L322 120Z" fill="#0c171e" stroke="#718894" stroke-width="4"/><rect x="116" y="72" width="55" height="30" rx="5" fill="#08151d" stroke="#55e8dc"/><text x="143" y="92" text-anchor="middle" class="label cyan" font-size="10">GIGE</text><path d="M102 142V183M55 183H150" stroke="#718894" stroke-width="10" stroke-linecap="round"/></g>
        <path d="M460 370C515 330 570 300 626 270" fill="none" stroke="#55e8dc" stroke-width="4" stroke-dasharray="12 12" opacity=".75"/><text x="480" y="348" class="label cyan" font-size="12">LIVE VIEW</text>
      </g>
    </g>
    <path id="future-cable" d="M696 857C850 900 1120 930 1428 890" class="cable"/><g id="future-source-plug"><circle r="17" fill="#07141c" stroke="#55e8dc" stroke-width="6"/><circle r="7" fill="#dffffa"/></g><g id="future-pulse"><circle r="18" fill="#dffffa" filter="url(#glow)"/></g>
    <g transform="translate(850 625)"><rect width="220" height="90" rx="24" fill="#102a39" stroke="#55e8dc" stroke-width="3"/><text id="detect-line" x="110" y="39" text-anchor="middle" fill="#f0fbff" font-size="18" font-weight="700">張力計を自動識別</text><text x="110" y="66" text-anchor="middle" class="micro">規格別アダプター</text></g>
    <g id="future-phone" transform="translate(1280 340)">
      <ellipse class="shadow" cx="165" cy="640" rx="195" ry="32"/><use href="#pdt-fp1-shell" width="330" height="620"/>
      <text x="40" y="70" fill="#f2fbff" font-size="22" font-weight="700">PDToscillo</text><text id="future-protocol" x="40" y="100" class="cyan label" font-size="13">TENSION / LAN</text>
      <g class="future-screen" data-index="0"><rect x="35" y="125" width="260" height="185" rx="10" fill="#0b2230"/><path id="tension-ui-wave" d="M48 260C80 205 105 277 137 216S192 266 224 201S270 254 283 214" class="wave" stroke-width="6"/><text x="40" y="350" class="micro">TENSION</text><text id="tension-value" x="40" y="395" fill="#f3fbff" font-size="40" font-weight="700">24.83</text><text x="164" y="395" class="cyan" font-size="17">kN</text><rect x="38" y="430" width="118" height="52" rx="9" fill="#123247"/><rect x="173" y="430" width="118" height="52" rx="9" fill="#123247"/><text x="97" y="462" text-anchor="middle" fill="#b8d2dd" font-size="13">PARAMETER</text><text x="232" y="462" text-anchor="middle" fill="#b8d2dd" font-size="13">SAVE</text></g>
      <g class="future-screen" data-index="1"><rect x="35" y="125" width="260" height="260" rx="10" fill="#0b2230"/><path d="M48 170H282M48 220H282M48 270H282M48 320H282M92 138V370M150 138V370M208 138V370M266 138V370" class="screen-grid"/><path id="scope-ui-wave" d="M48 282C85 145 112 347 151 188S217 330 251 178S278 249 283 222" class="wave" stroke-width="6"/><path d="M118 138V370M238 138V370" stroke="#ffb45d" stroke-width="3" stroke-dasharray="8 8"/><text id="scope-ui-value" x="40" y="430" fill="#f3fbff" font-size="28" font-weight="700">CH1  0.00 V</text><text x="40" y="472" class="micro">PROBE / TRIGGER / SCALE</text></g>
      <g class="future-screen" data-index="2"><rect x="35" y="125" width="260" height="345" rx="10" fill="#0b2230"/><text x="48" y="160" fill="#f3fbff" font-size="24" font-weight="700">DJ LIVE MIX</text><rect x="48" y="180" width="234" height="72" rx="8" fill="#132c3c"/><path id="dj-ui-wave-a" d="M58 216C78 186 96 240 116 200S153 235 176 194S218 240 270 190" fill="none" stroke="#55e8dc" stroke-width="5"/><path id="dj-ui-wave-b" d="M58 231C88 204 113 247 145 214S202 242 270 207" fill="none" stroke="#b785ff" stroke-width="5"/><g class="eq-bars" fill="#55e8dc"><rect x="58" y="286" width="18" height="88"/><rect x="88" y="266" width="18" height="108"/><rect x="118" y="302" width="18" height="72"/><rect x="195" y="278" width="18" height="96"/><rect x="225" y="252" width="18" height="122"/><rect x="255" y="294" width="18" height="80"/></g><path d="M70 404H260" stroke="#4a6370" stroke-width="8" stroke-linecap="round"/><g id="dj-ui-crossfader" transform="translate(165 404)"><rect x="-15" y="-13" width="30" height="26" rx="6" fill="#f3fbff" stroke="#55e8dc" stroke-width="3"/></g><text x="48" y="447" class="micro">DECK A   ·   CROSSFADER   ·   DECK B</text><text x="48" y="477" class="micro">DANTE NETWORK / LIVE</text></g>
      <g class="future-screen" data-index="3"><rect x="35" y="125" width="260" height="300" rx="10" fill="#16394d"/><circle cx="245" cy="184" r="35" fill="#ffb45d"/><path d="M35 425L98 282L155 345L212 235L295 350V425Z" fill="#245f78"/><path d="M35 425L112 350L171 401L232 339L295 386V425Z" fill="#173f55"/><g id="ui-bird"><path d="M0 8Q12-7 24 8Q36-7 48 8" fill="none" stroke="#f3fbff" stroke-width="5" stroke-linecap="round"/></g><path d="M205 160H270M238 143V178" stroke="#fff" stroke-width="4"/><rect x="58" y="455" width="95" height="32" rx="16" fill="#123247"/><text x="105" y="477" text-anchor="middle" class="micro">LIVE</text><text x="178" y="477" class="micro">1920 × 1080</text></g>
      <g transform="translate(78 606)"><path d="M-26 0V25H26V0" fill="#4d616c" stroke="#a9b7bd" stroke-width="3"/><path d="M-17 3V18M-7 3V18M3 3V18M13 3V18" stroke="#d7e0e4" stroke-width="3"/></g>
    </g>
    <text x="1810" y="975" text-anchor="end" class="micro">※ 自動判別・汎用UI切替は将来構想です。</text>
  </g>

  <g id="scene5" class="scene">
    <text x="100" y="168" class="scene-tag label">SCENE 05 / EDGE AI + CLOUD</text>
    <text x="100" y="250" class="headline">取得したデータを、現場で知能化する。</text>
    <text x="104" y="322" class="sub">PDT-FP1の計算処理とクラウドを組み合わせ、計測の次の価値へ。</text>
    <g transform="translate(1545 120)"><rect class="badge" width="250" height="48" rx="24"/><text x="125" y="32" text-anchor="middle" class="badge-text">今後の展望</text></g>
    <g transform="translate(95 395)">
      <rect width="460" height="440" rx="28" class="panel"/><text x="35" y="52" class="label cyan" font-size="17">MEASUREMENT DATA</text>
      <g transform="translate(35 90)"><rect width="390" height="85" rx="15" fill="#102838"/><text x="22" y="32" fill="#dcebf1" font-size="17" font-weight="700">張力・波形・音響・映像</text><path id="edge-source-wave" d="M22 64C60 36 88 82 126 48S194 82 235 43S304 77 365 38" fill="none" stroke="#55e8dc" stroke-width="6"/></g>
      <g transform="translate(35 197)"><rect width="390" height="85" rx="15" fill="#102838"/><text x="22" y="32" fill="#dcebf1" font-size="17" font-weight="700">連続する計測ストリーム</text><g fill="#55e8dc"><circle cx="35" cy="62" r="8"/><circle cx="83" cy="62" r="8"/><circle cx="131" cy="62" r="8"/><circle cx="179" cy="62" r="8"/><circle cx="227" cy="62" r="8"/><circle cx="275" cy="62" r="8"/><circle cx="323" cy="62" r="8"/></g></g>
      <g transform="translate(35 304)"><rect width="390" height="92" rx="15" fill="#0c202d" stroke="#315466" stroke-width="2"/><text x="22" y="36" class="micro">LAN INPUT</text><text x="22" y="70" fill="#f2fbff" font-size="23" font-weight="700">リアルタイムデータ</text><circle cx="345" cy="47" r="19" fill="#55e8dc" filter="url(#glow)"/></g>
    </g>
    <path id="edge-input-flow" d="M555 650C650 650 705 650 770 650" class="cable"/>
    <g id="edge-pdt-device" transform="translate(770 365) scale(.9)">
      <ellipse class="shadow" cx="165" cy="640" rx="190" ry="32"/><use href="#pdt-fp1-shell" width="330" height="620"/>
      <text x="47" y="92" fill="#f2fbff" font-size="22" font-weight="700">EDGE PROCESSING</text><text x="47" y="121" class="cyan label" font-size="12">ON-DEVICE / LIVE DATA</text>
      <g id="edge-ai-core" transform="translate(165 290)"><rect x="-96" y="-86" width="192" height="172" rx="22" fill="#102f3d" stroke="#55e8dc" stroke-width="4" filter="url(#glow)"/><path d="M-60-45H60M-60-10H60M-60 25H60" stroke="#2d5c68" stroke-width="7" stroke-dasharray="13 9"/><text x="0" y="-12" text-anchor="middle" fill="#f2fbff" font-size="31" font-weight="800">EDGE AI</text><text x="0" y="23" text-anchor="middle" class="cyan label" font-size="13">INFERENCE</text><g fill="#dffffa"><circle cx="-52" cy="56" r="8"/><circle cx="-18" cy="56" r="8"/><circle cx="18" cy="56" r="8"/><circle cx="52" cy="56" r="8"/></g></g>
      <g transform="translate(47 446)"><rect width="236" height="65" rx="16" fill="#142d38"/><text x="118" y="27" text-anchor="middle" class="micro">LOCAL PIPELINE</text><text x="118" y="50" text-anchor="middle" fill="#dcebf1" font-size="14" font-weight="700">前処理 → 推論 → 即時判定</text></g>
    </g>
    <path id="edge-local-flow" d="M1068 650C1200 650 1270 735 1360 755" class="cable"/>
    <path id="edge-cloud-flow" d="M1068 650C1200 650 1285 510 1395 485" class="cable"/>
    <g transform="translate(1370 405)">
      <g id="edge-cloud"><circle cx="105" cy="73" r="57" fill="#17394a" stroke="#55e8dc" stroke-width="4"/><circle cx="180" cy="58" r="73" fill="#17394a" stroke="#55e8dc" stroke-width="4"/><circle cx="255" cy="80" r="53" fill="#17394a" stroke="#55e8dc" stroke-width="4"/><rect x="102" y="72" width="158" height="65" rx="30" fill="#17394a"/><text x="180" y="91" text-anchor="middle" fill="#f2fbff" font-size="25" font-weight="800">CLOUD</text><text x="180" y="121" text-anchor="middle" class="micro">蓄積・共有・分析</text></g>
      <g transform="translate(0 220)"><rect width="360" height="205" rx="24" fill="#102838" stroke="#55e8dc" stroke-width="3"/><text x="30" y="48" class="label cyan" font-size="15">EDGE RESULT</text><text x="30" y="92" fill="#f2fbff" font-size="25" font-weight="700">異常検知</text><text x="190" y="92" fill="#f2fbff" font-size="25" font-weight="700">特徴抽出</text><text x="30" y="137" fill="#f2fbff" font-size="25" font-weight="700">即時判定</text><path d="M202 145L236 177L322 96" fill="none" stroke="#55e8dc" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/></g>
    </g>
    <g id="edge-pulses"><circle id="edge-in-pulse" r="15" fill="#dffffa" filter="url(#glow)"/><circle id="edge-local-pulse" r="15" fill="#dffffa" filter="url(#glow)"/><circle id="edge-cloud-pulse" r="15" fill="#dffffa" filter="url(#glow)"/></g>
    <text x="1810" y="975" text-anchor="end" class="micro">※ Edge AI・クラウド連携は今後の展望です。</text>
  </g>

  <g id="scene6" class="scene">
    <text x="960" y="185" text-anchor="middle" class="scene-tag label">PDTOSCILLO / PROJECT SUMMARY</text>
    <text x="960" y="285" text-anchor="middle" fill="#f3fbff" font-size="76" font-weight="800">計測データを、現場の価値へ。</text>
    <text x="960" y="350" text-anchor="middle" class="sub">機器接続から表示・操作・自動化・解析・共有までを、ひとつの拡張基盤に。</text>
    <g id="summary-card-1" transform="translate(110 470)"><rect width="390" height="260" rx="28" class="panel"/><circle cx="195" cy="78" r="42" fill="#123d43" stroke="#55e8dc" stroke-width="4"/><rect x="176" y="66" width="38" height="29" rx="5" fill="none" stroke="#55e8dc" stroke-width="5"/><path d="M184 95V104M194 95V104M204 95V104M164 65Q174 51 187 49M226 65Q216 51 203 49" fill="none" stroke="#55e8dc" stroke-width="5" stroke-linecap="round"/><text x="195" y="154" text-anchor="middle" class="label cyan" font-size="16">DISCOVER</text><text x="195" y="202" text-anchor="middle" fill="#f2fbff" font-size="26" font-weight="700">機器を自動識別</text></g>
    <g id="summary-card-2" transform="translate(550 470)"><rect width="390" height="260" rx="28" class="panel"/><circle cx="195" cy="78" r="42" fill="#123d43" stroke="#55e8dc" stroke-width="4"/><rect x="174" y="49" width="42" height="58" rx="7" fill="none" stroke="#55e8dc" stroke-width="5"/><path d="M181 72C187 59 192 83 199 66S210 79 212 61M181 92H209" fill="none" stroke="#55e8dc" stroke-width="4" stroke-linecap="round"/><text x="195" y="154" text-anchor="middle" class="label cyan" font-size="16">INTERACT</text><text x="195" y="202" text-anchor="middle" fill="#f2fbff" font-size="24" font-weight="700">表示・操作を最適化</text></g>
    <g id="summary-card-3" transform="translate(990 470)"><rect width="390" height="260" rx="28" class="panel"/><circle cx="195" cy="78" r="42" fill="#123d43" stroke="#55e8dc" stroke-width="4"/><path d="M177 62Q195 52 213 62V91Q195 102 177 91Z" fill="none" stroke="#55e8dc" stroke-width="5"/><path d="M177 62Q195 74 213 62M222 74Q230 88 218 100M168 88Q160 72 171 59" fill="none" stroke="#55e8dc" stroke-width="4" stroke-linecap="round"/><path d="M218 100L216 91M218 100L227 97" stroke="#55e8dc" stroke-width="4"/><text x="195" y="154" text-anchor="middle" class="label cyan" font-size="16">AUTOMATE</text><text x="195" y="202" text-anchor="middle" fill="#f2fbff" font-size="24" font-weight="700">記録・処理を自動化</text></g>
    <g id="summary-card-4" transform="translate(1430 470)"><rect width="390" height="260" rx="28" class="panel"/><circle cx="195" cy="78" r="42" fill="#123d43" stroke="#55e8dc" stroke-width="4"/><rect x="174" y="65" width="34" height="30" rx="5" fill="none" stroke="#55e8dc" stroke-width="4"/><path d="M181 58V65M192 58V65M203 58V65M181 95V103M192 95V103M203 95V103" stroke="#55e8dc" stroke-width="4"/><path d="M210 85Q218 70 229 79Q242 82 237 96H214" fill="none" stroke="#55e8dc" stroke-width="4" stroke-linecap="round"/><text x="195" y="154" text-anchor="middle" class="label cyan" font-size="16">INTELLIGENCE</text><text x="195" y="202" text-anchor="middle" fill="#f2fbff" font-size="23" font-weight="700">Edge AI・Cloud連携</text></g>
    <g id="summary-closing" transform="translate(460 815)"><rect width="1000" height="92" rx="46" fill="#0c2933" stroke="#55e8dc" stroke-width="3"/><text x="500" y="58" text-anchor="middle" fill="#dffffa" font-size="30" font-weight="700">計測を「見る」から、判断し活用する基盤へ。</text></g>
  </g>

  <rect id="wipe" class="cut" width="1920" height="1080"/>
  <g transform="translate(88 1010)"><rect width="1744" height="3" fill="#193a4a"/><rect id="progress" width="0" height="3" fill="#55e8dc"/></g>
</svg>`;

const $ = id => stage.querySelector(id);
const scenes = ['#scene1','#scene2','#scene3','#scene4','#scene5','#scene6'].map($);
const clamp = v => Math.max(0, Math.min(1, v));
const smooth = v => { v=clamp(v); return v*v*(3-2*v); };
function setTransform(selector, value) { $(selector)?.setAttribute('transform', value); }
function renderAt(raw) {
  const t = Math.min(Math.max(raw, 0), 59.999);
  const index = t < 9 ? 0 : t < 15 ? 1 : t < 24 ? 2 : t < 44 ? 3 : t < 54 ? 4 : 5;
  scenes.forEach((s,i) => s.style.opacity = i === index ? '1' : '0');
  $('#progress').setAttribute('width', String(1744 * t / 60));
  $('#parallax').setAttribute('transform', `translate(${-20*Math.sin(t*.18)} 0)`);
  const local = index === 0 ? t : index === 1 ? t-9 : index === 2 ? t-15 : index === 3 ? t-24 : index === 4 ? t-44 : t-54;
  if (index === 0) {
    const carry = smooth(local/5), strain = Math.sin(local*3)*4;
    setTransform('#heavy-worker', `translate(${790-125*(1-carry)} ${470+strain}) rotate(${-8+8*carry} 0 250)`);
    setTransform('#heavy-pc', `translate(${-270+75*carry} ${232+35*carry}) rotate(${-8+8*carry})`);
    $('#sweat').style.opacity = String(.55+.45*Math.abs(Math.sin(local*2.8)));
    $('#heavy-speech').style.opacity = String(clamp((local-1.7)/1.1));
    $('#legacy-window').style.opacity = String(clamp((local-3.1)/.9));
    $('#os-error').style.opacity = String(local > 4.5 ? .72+.28*Math.abs(Math.sin(local*3)) : 0);
    $('#legacy-cable').style.strokeDasharray = '18 13'; $('#legacy-cable').style.strokeDashoffset = String(-local*18);
  } else if (index === 1) {
    setTransform('#big-instrument', `translate(${1040+12*Math.sin(local*.7)} 245)`);
    setTransform('#confused-arms', `rotate(${6*Math.sin(local*2.5)} 0 140)`);
    setTransform('#price-tag', `translate(520 -35) rotate(${7+4*Math.sin(local*2)})`);
    $('#deep-menu').style.opacity = String(.65+.35*Math.abs(Math.sin(local*1.7)));
  } else if (index === 2) {
    const plug = smooth(clamp((local-.5)/1.7));
    setTransform('#pdt-device', `translate(${1500-50*plug} ${235-8*Math.sin(local*.7)}) rotate(${3-3*plug} 130 280)`);
    $('#pdt-ui').style.opacity = String(clamp((local-2.2)/.8));
    $('#pc-retired').style.opacity = String(clamp((local-1.0)/1.3));
    $('#lan-packets').style.opacity = String(local > 2.25 ? 1 : 0);
    const cable = $('#lan-cable'), length = cable.getTotalLength(), points = [0,.33,.67]; $('#lan-packets').querySelectorAll('circle').forEach((c,i)=>{const p=(local*.22+points[i])%1, point=cable.getPointAtLength(length*p); c.setAttribute('cx',point.x);c.setAttribute('cy',point.y);});
  } else if (index === 3) {
    const active = Math.min(3, Math.floor(local/5));
    const phase = (local%5)/5, motion = .5-.5*Math.cos(phase*Math.PI*2);
    const blend = active < 3 ? smooth(clamp((phase-.94)/.06)) : 0;
    const nextActive = Math.min(3, active+1), displayActive = blend > .5 ? nextActive : active;
    const labels=['張力計を自動識別','LXI / SCPIを自動識別','Dante DJを自動識別','GigEカメラを自動識別'];
    const protocols=['TENSION / LAN','LXI / SCPI','DANTE / AES67','GIGE VISION'];
    const actions=['車両を引張る → 張力値が上昇','プローブを接触 → 波形をライブ表示','DJ操作 → EQ・フェーダーへ同期','カメラで撮影 → 映像をライブ配信'];
    $('#detect-line').textContent = labels[displayActive]; $('#future-protocol').textContent = protocols[displayActive];
    $('#future-action').textContent = actions[displayActive];
    stage.querySelectorAll('.future-device').forEach((c,i)=>{const opacity=i===active?1-blend:i===nextActive?blend:0;c.style.opacity=String(opacity);c.style.filter=opacity>.5?'url(#glow)':'none';});
    stage.querySelectorAll('.future-screen').forEach((c,i)=>{const opacity=i===active?1-blend:i===nextActive?blend:0;c.style.opacity=String(opacity);});
    const starts=[[696,857],[763,860],[753,866],[390,792]], from=starts[active], to=starts[nextActive];
    const sx=from[0]+(to[0]-from[0])*blend, sy=from[1]+(to[1]-from[1])*blend;
    const phoneY=320-5*Math.sin(local*.9), phonePortX=1358, phonePortY=phoneY+610;
    const cable = $('#future-cable'); cable.setAttribute('d',`M${sx} ${sy}C${Math.max(820,sx+170)} ${sy+62} 1170 940 ${phonePortX} ${phonePortY}`);
    setTransform('#future-source-plug',`translate(${sx} ${sy})`);
    const cableFade=1-.65*Math.sin(blend*Math.PI); cable.style.opacity=String(cableFade);$('#future-source-plug').style.opacity=String(cableFade);$('#future-pulse').style.opacity=String(cableFade);
    const length = cable.getTotalLength(), point = cable.getPointAtLength(length*((local*.35)%1));
    setTransform('#future-pulse', `translate(${point.x} ${point.y})`);
    setTransform('#future-phone', `translate(1280 ${phoneY})`);
    setTransform('#future-test-car',`translate(${42*motion} 0)`);
    setTransform('#future-pull-arrow',`translate(${575+42*motion} 180)`);
    $('#future-pull-arrow').style.opacity=String(.45+.55*motion);
    $('#future-tension-link').setAttribute('d',`M${66+42*motion} 190C${18+15*motion} 210 12 265 3 310`);
    $('#future-tension-led').setAttribute('r',String(14+8*motion));
    $('#tension-value').textContent=(18.5+11.4*motion).toFixed(2);
    $('#tension-ui-wave').setAttribute('d',`M48 ${267-25*motion}C80 ${220-18*motion} 105 277 137 ${230-35*motion}S192 266 224 ${215-28*motion}S270 254 283 ${225-30*motion}`);
    const contact=smooth(clamp((phase-.1)/.28));
    setTransform('#scope-probe',`translate(${48*(1-contact)} ${-25*(1-contact)})`);
    $('#scope-contact-indicator').style.opacity=String(contact);
    ['#scope-left-wave','#scope-ui-wave'].forEach(selector=>{const wave=$(selector);wave.style.strokeDasharray='700';wave.style.strokeDashoffset=String(700*(1-contact));wave.style.opacity=String(.15+.85*contact);});
    $('#scope-ui-value').textContent=`CH1  ${(2.48*contact).toFixed(2)} V`;
    setTransform('#dj-jog-left',`translate(178 300) rotate(${phase*720})`);
    setTransform('#dj-jog-right',`translate(522 300) rotate(${-phase*620})`);
    setTransform('#dj-crossfader',`translate(${336+52*(motion-.5)} 427)`);
    setTransform('#dj-ui-crossfader',`translate(${165+72*(motion-.5)} 404)`);
    stage.querySelectorAll('.eq-bars rect').forEach((b,i)=>{const h=52+72*Math.abs(Math.sin(local*3+i*.8));b.setAttribute('height',String(h));b.setAttribute('y',String(374-h));});
    $('#dj-ui-wave-a').style.strokeDasharray='15 8';$('#dj-ui-wave-a').style.strokeDashoffset=String(-phase*120);
    $('#dj-ui-wave-b').style.strokeDasharray='12 7';$('#dj-ui-wave-b').style.strokeDashoffset=String(phase*105);
    const bird = (local*.23)%1;
    setTransform('#world-bird', `translate(${340+210*bird} ${150+8*Math.sin(bird*Math.PI*2)})`);
    setTransform('#ui-bird', `translate(${62+125*bird} ${220+5*Math.sin(bird*Math.PI*2)})`);
  } else if (index === 4) {
    const flows=[['#edge-input-flow','#edge-in-pulse',.34,0],['#edge-local-flow','#edge-local-pulse',.28,.18],['#edge-cloud-flow','#edge-cloud-pulse',.25,.52]];
    flows.forEach(([pathSelector,pulseSelector,speed,offset],i)=>{const path=$(pathSelector), length=path.getTotalLength(), p=(local*speed+offset)%1, point=path.getPointAtLength(length*p);setTransform(pulseSelector,`translate(${point.x} ${point.y})`);$(pulseSelector).style.opacity=String(i===0?1:clamp((local-2)/1.5));});
    const corePulse=1+.045*Math.sin(local*3.2); setTransform('#edge-ai-core',`translate(165 290) scale(${corePulse})`);
    $('#edge-source-wave').style.strokeDasharray='18 10';$('#edge-source-wave').style.strokeDashoffset=String(-local*35);
    setTransform('#edge-cloud',`translate(0 ${-5*Math.sin(local*.9)})`);
  } else {
    const cards=[['#summary-card-1',110],['#summary-card-2',550],['#summary-card-3',990],['#summary-card-4',1430]];
    cards.forEach(([selector,x],i)=>{const p=smooth(clamp((local-.35-i*.35)/.7));setTransform(selector,`translate(${x} ${470+70*(1-p)})`);$(selector).style.opacity=String(p);});
    const close=smooth(clamp((local-2.1)/.8));setTransform('#summary-closing',`translate(460 ${815+45*(1-close)})`);$('#summary-closing').style.opacity=String(close);
  }
  const nearest = [0,9,15,24,44,54,60].reduce((a,b)=>Math.abs(t-b)<Math.abs(t-a)?b:a,0);
  const d = Math.abs(t-nearest); const wipe = d < .28 ? 1-d/.28 : 0;
  $('#wipe').style.opacity=String(wipe*.86); $('#wipe').setAttribute('x',String((1-wipe)*1920));
}
window.__renderFrame = renderAt; window.__duration = 60; window.__variant = 'product-pv';
renderAt(0);
if (!new URLSearchParams(location.search).has('capture')) {
  const tick = ms => { renderAt((ms/1000)%60); requestAnimationFrame(tick); }; requestAnimationFrame(tick);
}
