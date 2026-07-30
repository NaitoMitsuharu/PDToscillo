# 実機確認が必要な項目

自動テスト（疑似オシロスコープ相手）で検証できた項目と、実機でしか確認できない項目を分離して記録します。
**推測による成功報告はしません。** 実機未確認の項目はここに残り続けます。

## 実機接続時の記録欄

実機へ最初に接続したら、以下を記録してください。この情報でモデルと世代が確定し、
コマンド対応表を正確に絞り込めます。

```text
*IDN? の応答:
（ここへ貼り付け）

例: TEKTRONIX,MDO4104C,C012345,CF:91.1CT FV:v1.28
    → メーカー / モデル / シリアル / ファームウェア
```

記録後、[compatibility-matrix.md](compatibility-matrix.md) の該当列を `?` から確定値へ更新します。

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

- **確認対象**: モバイル回線ではなく Ethernet 経由で接続されること
- **実行するコマンド**: モバイル通信と Wi-Fi を有効にしたまま接続診断を実行
- **期待する応答**: 「経路検証: Ethernet 経由」。ソケットのローカルアドレスが Ethernet の IP と一致
- **失敗時の確認事項**:
  - バインド方式を `socketFactory` と `bindSocket` で切り替えて再試行
  - Wi-Fi を切って挙動が変わるか
  - VPN が有効になっていないか

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

実行方法:

```bash
./gradlew testDebugUnitTest :core:common:test :core:scpi:test
./gradlew :app:connectedDebugAndroidTest
```
