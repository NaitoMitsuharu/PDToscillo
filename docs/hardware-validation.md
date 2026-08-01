# 実機確認が必要な項目

自動テスト（疑似オシロスコープ相手）で検証できた項目と、実機でしか確認できない項目を分離して記録します。
**推測による成功報告はしません。** 実機未確認の項目はここに残り続けます。

---

## 確認済み: PDT-FP1 実機 + 疑似サーバー（2026-08-01）

オシロスコープ（DPO4034）は手元に無いが、**PDT-FP1 実機は手元にある**状態で、
疑似サーバーを DPO4034 の代役にして「ネットワーク/バインド層とアプリ動作」を実機検証した。

構成:

```text
PC(開発機) ── USB(Type-C, adb) ── PDT-FP1 実機
PC(開発機) ── LAN ケーブル ──── PDT-FP1 実機（イーサネットテザリング）
PC 上で疑似サーバーを DPO4034 として起動: :simulator:run --bind 0.0.0.0 --model DPO4034
アプリは PDT-FP1 →(eth0)→ PC:4000 の疑似サーバーへ接続
```

このとき **モバイルデータ ON・Wi-Fi ON**（多経路同時）を再現。

### 実機で確定した事実

- **eth0 はイーサネットテザリング**（`dumpsys tethering` に `TETHERING_ETHERNET` /
  `state=TETHERED` / `10.52.125.152/24` を確認）。`ConnectivityManager` に
  `TRANSPORT_ETHERNET` として現れないため `socketFactory` / `bindSocket` は使えない。
  → 設計前提がシステムログで裏付けられた。
- **アプリの `NetworkInterface` 列挙は eth0 を取得できる**（Android 15）。
  セッションログに `eth0 ... ethernetらしい=true addr=... 10.52.125.152/24` を記録。
- **「有線を固定」(ETHERNET_INTERFACE_ADDRESS) が実機で機能する。**
  PC 側 `Get-NetTCPConnection` で、4000 番への接続の **接続元が 10.52.125.152 (eth0)**
  であることを確認。モバイル・Wi-Fi 同時 ON でも eth0 から出た（誤ルーティングなし）。
- **接続 → `*IDN?` → 機能検出（Gen1 のモデル名フォールバック）** が動作。
  疑似 DPO4034 は `CONFIGuration:ANALOg:NUMCHANnels?` に無応答 → アプリは `EVMsg?` で
  `113,"Undefined header"` を検出 → モデル名から アナログ4/デジタル0 を判定。
- **概要・波形画面が実機で動作**。波形はバイナリブロック（`CURVe?`）転送→デコード→
  間引き→Compose 描画まで動作（10000 点、257 KiB/s、約 1.0 Hz）。
- **LAN 自動接続が動作**。前回接続先を端末に永続化し、アプリ再起動後に手動操作ゼロで
  eth0 から自動接続。手動「切断」後は自動再接続しない（挿し直しで再開）。
- Android バージョンは **Android 15 (API 35)**（旧ドキュメントの 14 想定を訂正）。

### これでも確認できていない（オシロ実機が要る）

- 本物の DPO4034 の `*IDN?` 生文字列、Gen1 実機での `CONFIGuration:*?` の実際の可否
- 実信号の波形データ・電圧スケーリングの正しさ、Socket Server always-on の実挙動
- 実機オシロ相手の実効スループット（上記 257 KiB/s は疑似サーバー相手の値）

> 要するに **「PDT-FP1 上でのアプリの通信・経路・バインド・UI」は実機確認済み**。
> 残るのは DPO4034 固有の SCPI 挙動のみ。

---

## 確認済み実機: Tektronix DPO4034

接続日: 2026-07-31

### 機器情報

```text
モデル   : DPO4034
世代     : Gen 1（無印、B/C サフィックスなし）
ファームウェア: v2.48（DPO4000 シリーズの最終リリース付近）
アナログ CH: 4ch（モデル名末尾「4」より確定）
デジタル CH: なし（DPO プレフィックスより確定）
帯域     : 350 MHz（モデル名「034」→ 帯域コード「03」= 350 MHz）

*IDN? の応答: 未取得（接続は成功したがログ取得前に切断）
→ 次回接続時にセッションログを先に開始して記録すること
```

### ネットワーク接続

```text
構成     : PC(USB) ─ PDT-FP1(LAN) ─ DPO4034
LAN ドライバ: Realtek r8168（adb shell ifconfig eth0 で確認）
Android 側 I/F: eth0（UP, BROADCAST, RUNNING, MULTICAST）

Android の挙動（重要）:
  eth0 は ConnectivityManager に TRANSPORT_ETHERNET として登録されず、
  代わりに「テザリング用インターフェース（Tethering interface mode: 1）」
  として管理される。Settings の「イーサネット」項目が表示されない。

設定手順:
  1. PDT-FP1: 設定 → ネットワーク → ホットスポットとテザリング
     → イーサネットテザリング ON
  2. DPO4034: Utility → I/O → Ethernet Network Settings → DHCP/BOOTP → ON
  3. DPO4034 の IP を Change Instrument Settings → Instrument IP Address で確認

実際に割り当てられた IP:
  PDT-FP1 (eth0)  : 10.175.225.170 / 24
  DPO4034         : 10.175.225.142 / 24（DHCP 払い出し）

疎通確認（adb shell ping）:
  ping -c 4 10.175.225.142 → 0% packet loss, avg 0.5 ms

ポート確認（adb shell nc -z）:
  nc -z -w 3 10.175.225.142 4000 → exit:0（ポート開放を確認）
```

### Socket Server（DPO4034 Gen 1 固有の挙動）

```text
UI メニュー: 存在しない
  Utility → I/O → Ethernet Network Settings に表示されるのは
    - Change Instrument Settings
    - DHCP/BOOTP
    - Test Connection
  のみ。e*Scope、Socket Server のトグルは一切ない。

挙動: Socket Server はファームウェア内で常時有効（always-on）
  メニューで ON/OFF する必要がなく、DPO4034 に IP が割り当てられれば
  ポート 4000 は自動的に開いている。

Protocol: None（Terminal モードではない）
Port    : 4000
接続確認: TCP 接続成功、SCPI 応答あり（接続後 4 通信を記録）
```

### アプリ側の必須設定

```text
バインド方式: 有線を固定（ETHERNET_INTERFACE_ADDRESS）を推奨
  テザリングモードでは ConnectivityManager が eth0 を認識しないため、
  ETHERNET_SOCKET_FACTORY / ETHERNET_BIND_SOCKET は両方失敗する
  （Network オブジェクトが無く、バインド先を指定できないため）。

  そこで「有線を固定」方式を追加した。Network 抽象を経由せず、
  NetworkInterface 列挙で得た eth0 の IP（10.175.225.170）を
  Socket.bind() でソースアドレスに固定する。宛先 10.175.225.142 は
  eth0 の直結サブネット上にあるため、OS は最長プレフィックス一致で
  必ず eth0 の経路を選ぶ。モバイル回線へ漏れない。
  → この方式が実機で eth0 経由になることは HARDWARE_VALIDATION_REQUIRED #2 で確認する。

  自動接続は「有線を固定」を先に試し、使えない環境では
  SYSTEM_DEFAULT へ自動フォールバックする。SYSTEM_DEFAULT でも
  10.175.225.x は直結サブネット経由で eth0 に載るため接続自体は成立する。

IP   : 10.175.225.142（DHCP なので再起動時に変わる可能性あり）
       → オシロ前面パネルで静的 IP に固定するとサブネットが安定する
         （docs/network-setup.md 参照）。アプリからは IP を書き換えない。
Port : 4000
Terminator: LF
Protocol  : Raw Socket（VXI-11 は未実装）
```

### 波形データフォーマット（コードと仕様書から）

```text
転送プロトコル: SCPI テキスト over TCP port 4000
レスポンス終端: LF (\n)

波形取得シーケンス:
  1. DATa:SOUrce CH1       ← 対象チャンネル指定
  2. DATa:ENCdg RIBinary   ← バイナリエンコード（読み取り専用時はスキップ）
  3. DATa:WIDth 1          ← 1 バイト / 点（または 2 バイト）
  4. WFMOutpre?            ← スケール情報取得
  5. CURVe?                ← 波形データ取得

WFMOutpre の主要フィールド（CSV 形式で返却）:
  BYT_NR  : バイト/点（1 or 2）
  NR_PT   : 点数（デフォルト: 10000）
  XINCR   : 時間分解能 [s/点]
  XZERO   : 先頭点の時刻
  YMULT   : 電圧換算係数 [V/count]
  YOFF    : ゼロオフセット [count]
  YZERO   : 電圧オフセット [V]

電圧換算式:
  voltage[V] = (raw_count - YOFF) × YMULT + YZERO

CURVe? レスポンス形式（IEEE 488.2 バイナリブロック）:
  # <N> <NNNN...> <data bytes>
  例: #510000 + 10000 bytes（10000点 × 1バイト）
      #520000 + 20000 bytes（10000点 × 2バイト）

更新レート（実測値なし、以下は推定）:
  LAN RTT       : ~0.5 ms
  10000点×1バイト: 10KB + SCPI オーバーヘッド → 推定 10〜30 ms
  アプリ最小間隔 : 200 ms（設定可能: 200/500/1000/2000/5000 ms）
  実用的な更新率: 単チャンネル・デフォルト記録長で 2〜5 Hz 程度
  → 正確な値は次回接続時にアプリのスループット表示で確認
```

---

## 実機接続時の記録欄（次回接続用）

```text
*IDN? の応答:
（ここへ貼り付け）

例: TEKTRONIX,DPO4034,C012345,CF:91.1CT FV:v2.48
    → メーカー / モデル / シリアル / ファームウェア
```

記録後、[compatibility-matrix.md](compatibility-matrix.md) の該当列を `?` から確定値へ更新します。

**接続する前にアプリの「詳細設定」→「セッションログ」→「記録開始」を押してください。**
送受信した SCPI とネットワークの状態がすべて端末内のファイルへ残ります。

---

---

## HARDWARE_VALIDATION_REQUIRED

### 1. PDT-FP1 の LAN 端子が Android の Ethernet として認識されるか

- **確認対象**: `ConnectivityManager` が `TRANSPORT_ETHERNET` のネットワークを報告するか
- **実行するコマンド**: アプリの接続画面 →「接続診断」を実行
- **期待する応答**: 「Ethernet 検出: 検出」と、`eth0` 相当のインターフェース名・IP アドレスの表示
- **失敗時の確認事項**:
  - LAN ケーブルが両端で正しく挿さっているか
  - 端末の `設定` → `ネットワークとインターネット` に「イーサネット」項目があるか
  - PDT-FP1 の LAN 端子がカメラ接続専用の扱いになっていないか
  - 診断画面の `NetworkInterface` 一覧に該当インターフェースが出ているか
    （出ているが `TRANSPORT_ETHERNET` として報告されない場合はバインド無しで接続を試す）

### 2. Ethernet へのソケットバインドが実際に効くか

- **状況（DPO4034 / 2026-07-31）**: テザリング扱いで `TRANSPORT_ETHERNET` が無く、
  `socketFactory` / `bindSocket` は使えないことが判明。対策として
  **「有線を固定」方式（ETHERNET_INTERFACE_ADDRESS）** を追加した。
  eth0 の IP をソケットのソースアドレスに `Socket.bind()` で固定する方式で、
  Android の Network オブジェクトを必要としない。
- **確認対象**: 「有線を固定」でモバイル回線ではなく eth0 経由で接続されること
- **実行するコマンド**: モバイル通信と Wi-Fi を有効にしたまま、バインド方式「有線を固定」で
  接続 → 接続診断を実行。あわせて `adb shell ip route get 10.175.225.142` で
  `dev eth0` を通ることを直接確認する
- **期待する応答**: 「経路検証: Ethernet 経由（警告なし）」。
  ソケットのローカルアドレスが eth0 の IP（例 10.175.225.170）と一致
- **失敗時の確認事項**:
  - 「有線を固定」で bind に失敗する場合は例外メッセージをセッションログで確認
  - `SYSTEM_DEFAULT` でも `ip route get` が `dev eth0` を返すか（直結サブネットなら返るはず）
  - Wi-Fi / モバイルがオシロと同一サブネットを掴んでいないか（サブネット衝突）
  - VPN が有効になっていないか
- **自動テスト状況**: ソースアドレス固定 → 接続 → `*IDN?` の一連は
  `InterfaceAddressBindingIntegrationTest`（ループバックで eth0 を代用）で検証済み。
  ただし**実機の eth0 で本当に有線側に載るか**はここで確認する必要がある。

### 3. Socket Server の既定ポートとプロトコル

- **確認対象**: ポート 4000 / `Protocol: None` で SCPI が通ること
- **実行するコマンド**: `*IDN?`
- **期待する応答**: `TEKTRONIX,` から始まるカンマ区切り文字列 + 終端 LF
- **失敗時の確認事項**:
  - オシロスコープ側 `Utility` → `I/O` → `Socket Server` が `Enabled`
  - ポート番号がアプリ側と一致しているか
  - `Protocol` が `Terminal` になっていないか（アプリが検出して警告します）
  - 他のアプリや PC が既に接続していないか（同時接続数の制限）

### 4. Gen 1（DPO4000 / MSO4000）での `CONFIGuration:*?` の可否

- **確認対象**: `CONFIGuration:*?` クエリ群が Gen 1 に存在するか
- **実行するコマンド**: `CONFIGuration:ANALOg:NUMCHANnels?` の直後に `*ESR?` と `EVMsg?`
- **期待する応答**: 数値が返る（対応）／未定義ヘッダーエラーが返る（非対応）
- **失敗時の確認事項**:
  - 非対応の場合はモデル名推定へフォールバックしていること
  - フォールバック後もアプリがクラッシュしないこと
  - 誤ってチャンネル数を過大に見積もっていないこと

### 5. 波形転送の実データ

- **確認対象**: `WFMOutpre?` の各フィールドと `CURVe?` のバイナリブロック
- **実行するコマンド**:
  ```text
  DATa:SOUrce CH1
  DATa:STARt 1
  DATa:STOP <レコード長>
  DATa:ENCdg RIBinary
  DATa:WIDth 1
  WFMOutpre?
  CURVe?
  ```
- **期待する応答**: プリアンブルに `BYT_NR`/`BIT_NR`/`ENCDG`/`BN_FMT`/`BYT_OR`/`NR_PT`/
  `XINCR`/`XZERO`/`PT_OFF`/`YMULT`/`YOFF`/`YZERO` が含まれ、`CURVe?` が
  `#<桁数><長さ>` に続くバイナリを返す
- **失敗時の確認事項**:
  - 対象チャンネルが表示状態か（非表示だとエラーになる）
  - `DATa:WIDth` が 2 のときにプリアンブルの `BYT_NR` も 2 になっているか
  - 実測のバイト数がプリアンブルの `NR_PT` × `BYT_NR` と一致するか
  - 電圧値が本体画面の表示と一致するか（スケーリング式の検証）

### 6. e*Scope の利用可否

- **確認対象**: `http://<IP>/` でブラウザから本体画面が見えるか
- **実行するコマンド**: なし（ブラウザでアクセス）
- **期待する応答**: e*Scope の画面が表示される
- **失敗時の確認事項**:
  - HTTP ポートが 80 以外に変更されていないか
  - 機種が e*Scope に対応しているか

### 7. VXI-11

- **確認対象**: VXI-11（ONC RPC, ポート 111 / portmap）が有効か
- **実行するコマンド**: 本アプリでは未実装。診断画面のポート探索で 111 を確認
- **期待する応答**: —
- **失敗時の確認事項**: → [vxi11-feasibility.md](vxi11-feasibility.md)

---

## 自動テスト済み（実機不要）

疑似オシロスコープ（`:simulator`）を相手に自動テストで検証済みの項目です。
**実機で確認したことにはなりません。** 上記の HARDWARE_VALIDATION_REQUIRED とは明確に区別しています。

### Phase 1

| 項目 | 検証内容 | テスト |
| --- | --- | --- |
| TCP 接続 | 接続 → `*IDN?` → 応答解析 | `RawSocketTransportIntegrationTest` |
| 応答の分割受信 | 3 バイトずつ届く状況でテキスト応答を解析 | 同上 |
| バイナリの分割受信 | 13 バイトずつ届く 5,000 点の `CURVe?` を読み切る | 同上 |
| 16 bit 波形 | `DATa:WIDth 2` で 1,000 点 = 2,000 バイト | 同上 |
| 不正なブロック長 | 宣言長と実データの不一致を検出 | 同上 |
| 過大なブロック長 | 上限超過を拒否しメモリを確保しない | 同上 |
| 読み取りタイムアウト | 応答が返らない場合の分類と再試行可否 | 同上 |
| 応答途中の切断 | 不完全な応答を成功として扱わない | 同上 |
| 切断 → 再接続 | 再接続後も `*IDN?` が取れる | 同上 |
| 同期喪失からの復帰 | タイムアウト後に遅延応答を破棄し、次の応答がずれない | 同上 |
| コマンドの直列化 | 5 本同時発行でも応答の取り違えが起きない | 同上 |
| 読み取り専用モード | 設定変更を拒否し、値が変わらない | 同上 |
| 設定の読み戻し | 変更前 → 設定 → 再 Query で受理値を取得 | 同上 |
| 未定義コマンド | 応答なし + イベント 113 を検出し、クラッシュしない | 同上 |
| Terminal プロトコル検出 | エコーを検出し None への変更を案内 | 同上 |
| 通信ログ | バイナリ本体を残さずサイズと SHA-256 のみ記録 | 同上 |
| Capability（Configuration 経路） | `CONFIGuration:*?` から 4ch/16ch/RF/AFG/DVM/バスを取得 | `CapabilityDetectionIntegrationTest` |
| Capability（フォールバック経路） | 未定義ヘッダー検出 → モデル名推定へ切り替え | 同上 |
| Capability の過大評価防止 | 判定不能なオプションを有効化せず「不明」として記録 | 同上 |
| 検出の非破壊性 | 検出前後で設定値が変化しない | 同上 |
| `*IDN?` 解析 | 4 要素 / 要素不足 / ヘッダ付き / 他社製 | `IdnParserTest` |
| モデル名推定 | DPO/MSO/MDO、2ch/4ch、世代、4000 系以外 | `ModelNameResolverTest` |
| 応答解析 | 引用符内の区切り文字、ヘッダ有無、複合応答 | `ScpiResponseParserTest` |
| IEEE 488.2 ブロック | 1 バイトずつの受信、CRLF 終端、`#0` 非対応、異常長 | `ScpiBinaryBlockReaderTest` |
| エラー分類 | マニュアル Table 3-5 / 3-6 の実コード | `ScpiErrorQueueTest` |
| 危険コマンド判定 | `*RST` / `FACTORY` / ファイル削除 / AFG 出力 ON | `ScpiDangerClassifierTest` |
| 工学単位 | ns/µs/mV/MHz の整形と `1.5n` などの解釈 | `EngineeringUnitsTest` |
| 接続画面 UI | 表示、入力検証、接続、エラーと対処の表示、読み取り専用 | `ConnectionScreenTest`（エミュレータ実行） |

### Phase 2〜6

| 項目 | 検証内容 | テスト |
| --- | --- | --- |
| Acquisition 読み書き | モード / 停止条件 / Average 回数 | `Tektronix4000DriverIntegrationTest` |
| Run / Stop / Single | 状態が実際に変わる。Single は STOPAfter → RUN の順 | 同上 |
| 水平軸 | 時間軸・レコード長の読み書きと 10 div 換算 | 同上 |
| チャンネル設定 | 表示・スケール・カップリング・ラベル・プローブ減衰比 | 同上 |
| DC と DCREJect | 前方一致で誤判定しない | 同上 |
| ラベルの引用符除去 | 引用符入りの入力でコマンドが壊れない | 同上 |
| 設定の読み戻し | 送った値ではなく本体が受理した値を返す | 同上 |
| 読み取り専用の拒否 | 拒否後に値が変わっていない | 同上 |
| 波形転送 | プリアンブルの値でスケーリングされる（手計算と一致） | `WaveformTransferIntegrationTest` |
| 16 bit 転送 | 500 点 = 1,000 バイト | 同上 |
| 非表示チャンネル | 転送パラメータのみ返る状態を検出して失敗させる | 同上 |
| 読み取り専用での取得 | `DATa:*` を送らず本体の現在設定のまま取得 | 同上 |
| 間引き | 100,000 点を 1,080 点へ。ピークは保持、元データは不変 | 同上 |
| 連続取得 | 5 回連続で応答が混ざらない | 同上 |
| 測定 | 割り当て・削除・統計・即時測定・複数スロット | `MeasurementIntegrationTest` |
| 測定不可の判別 | 9.91e37 を数値として扱わない | 同上 |
| ファイル名検証 | 親ディレクトリ参照・引用符・改行・セミコロンを拒否 | `InstrumentFileControllerTest` |
| 波形デコード | 8/16 bit、符号有無、MSB/LSB、Envelope、RF float、Digital Collection | `WaveformDecoderTest` |
| プリアンブル解析 | ヘッダ有無の両形式、WFID のカンマ、欠損検出 | `WfmOutpreParserTest` |
| 間引きの性能 | 1,000 万点を 1 秒以内 | `MinMaxDecimatorTest` |
| 波形画面 | 取得・描画・カーソル・CSV/PNG 保存・自動スケール | `WaveformScreenTest`（エミュレータ実行） |
| 画面遷移 | 実装済みの全画面へ到達できる | `NavigationTest`（エミュレータ実行） |
| セッションログ | ヘッダ・ネットワーク状態・SCPI 全件・`*IDN?` の生応答の記録 | `SessionLogTest`（エミュレータ実行） |
| ログのバイナリ扱い | 本体を書かずサイズと SHA-256 のみ | 同上 |
| ログの保護 | 記録中のファイルと保存先の外は削除できない | 同上 |
| ログの上限 | 上限到達で停止し、先頭の内容は残る | `SessionLogWriterTest` |

実行方法:

```bash
./gradlew testDebugUnitTest :core:common:test :core:scpi:test :core:model:test :core:waveform:test :simulator:test
./gradlew :app:connectedDebugAndroidTest
```

---

## 実機で確認すべき追加項目（Phase 2〜6）

### 8. 設定変更が本体へ反映されるか

- **確認対象**: 垂直スケール、カップリング、レコード長などの変更が本体画面へ反映されるか
- **実行するコマンド**: アプリのチャンネル設定画面から変更 → 本体画面を目視
- **期待する応答**: アプリが表示する「受理された値」と本体画面の表示が一致する
- **失敗時の確認事項**:
  - 離散値へ丸められていないか（アプリは丸め後の値を表示する）
  - 読み取り専用モードが解除されているか
  - SCPI コンソールで同じコマンドを送って反応するか

### 9. 波形の電圧値が本体表示と一致するか

- **確認対象**: スケーリング式の実地検証
- **実行するコマンド**: CH1 に既知の信号（例: 1 kHz / 1 Vpp）を入れて取得
- **期待する応答**: アプリの波形の振幅と本体画面の測定値が一致する
- **失敗時の確認事項**:
  - プローブ減衰比の設定が本体とアプリで一致しているか
  - `WFMOutpre?` の `YMULT` / `YOFF` / `YZERO` を SCPI コンソールで確認
  - Peak Detect / Envelope モードになっていないか（`PT_FMT` が `ENV` かどうか）

### 10. 連続取得時の実効レート

- **確認対象**: 取得周期と本体の更新速度の関係
- **実行するコマンド**: 波形画面で連続取得を 200 ms 周期に設定
- **期待する応答**: スループット表示が安定し、要求が積み上がらない
- **失敗時の確認事項**:
  - レコード長を短くすると改善するか
  - 本体の通常操作が重くなっていないか（競合の兆候）
  - 周期を 1 s へ伸ばして安定するか

### 11. AFG 出力

- **確認対象**: 出力が実際に出るか、確認ダイアログが機能するか
- **実行するコマンド**: オプション画面から AFG を設定 → 出力を有効化
- **期待する応答**: 確認ダイアログの後に出力が有効になり、`AFG:OUTPut:STATE?` が 1 を返す
- **失敗時の確認事項**:
  - `CONFIGuration:AFG?` が 1 を返しているか（オプション搭載）
  - 読み取り専用モードが解除されているか

### 12. 本体のファイル操作

- **確認対象**: 保存・一覧・取り込み・削除
- **実行するコマンド**: ファイル画面から設定を保存 → 一覧を更新 → 取り込み
- **期待する応答**: `FILESystem:DIR?` に保存したファイルが現れる
- **失敗時の確認事項**:
  - 保存先ドライブ（`FILESystem:CWD?`）が期待どおりか
  - ファイル名に使えない文字が含まれていないか
  - `FILESystem:READFile` の応答がブロック形式か
