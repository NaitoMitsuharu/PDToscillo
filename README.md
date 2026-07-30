# PDToscillo

PDToscillo は、Sony PDT-FP1（Android 端末）から Tektronix 4000 シリーズ・オシロスコープを LAN 経由で
遠隔操作するアプリです。LAN ケーブルで直結し、波形取得、表示、測定、設定変更、保存、遠隔操作を行います。

```text
Sony PDT-FP1                                Tektronix 4000 Series
┌──────────────────┐   LAN ケーブル直結    ┌──────────────────────┐
│ PDToscillo       │──────────────────────▶│ Socket Server        │
│  Ethernet Network│   TCP / port 4000     │  Protocol: None      │
│  へソケットバインド│◀──────────────────────│  SCPI                │
└──────────────────┘                       └──────────────────────┘
```

PDT-FP1 では Ethernet・Wi-Fi・モバイル回線が同時に有効になり得ます。しかも LAN 直結では
インターネット到達性が無いため、Ethernet は既定ルートに選ばれません。**何もしないと
モバイル回線へ出てしまいます。** 本アプリはソケットを Ethernet の `Network` へ明示的に
バインドし、接続後にローカルアドレスを検証して誤ルーティングを検出します。

## 状態

全 6 フェーズを実装済みです。ただし**実機のオシロスコープには未接続**です。
検証済みの範囲は [docs/hardware-validation.md](docs/hardware-validation.md) で
「自動テスト済み」と「実機確認が必要」に分けて記載しています。

- [x] Phase 0: マルチモジュール構成、ビルド基盤、静的解析
- [x] Phase 1: Ethernet 検出・ソケットバインド・TCP 接続・`*IDN?`・接続診断・疑似サーバー
- [x] Phase 2: SCPI コマンドキュー・エラー処理・Capability 検出・チャンネル設定
- [x] Phase 3: 波形転送・バイナリブロック解析・Compose 描画・CSV/PNG/JSON 保存
- [x] Phase 4: トリガ（種別ごとに画面分離）・測定と統計
- [x] Phase 5: デジタル/バス・Spectrum・AFG・DVM・ファイル操作・SCPI コンソール
- [x] Phase 6: 自動測定シーケンス・Room 保存・設定画面

## 対応予定モデル

判明している情報は「Tektronix 4000 シリーズ」のみで、正確なモデルは未確定です。
特定モデル専用の実装はせず、接続後に `*IDN?` で識別して対応機能だけを有効化します。

| 世代 | モデル例 |
| --- | --- |
| 無印 | DPO4000, MSO4000 |
| B / MDO 初代 | DPO4000B, MSO4000B, MDO4000 |
| B / C | MDO4000B, MDO4000C |

**現在確認済みの実機モデル: なし**（実機接続後に `*IDN?` の応答をここへ追記します）

詳細は [docs/compatibility-matrix.md](docs/compatibility-matrix.md) を参照してください。

## 必要環境

- Android Studio（AGP 8.10.1 に対応するバージョン）
- JDK 17（Android Studio 同梱の JBR で可）
- Android SDK Platform 36 / minSdk 30
- Sony PDT-FP1 または Android 11 以降の Android 端末

## ビルド

```bash
./gradlew assembleDebug
```

`java` が PATH に無い環境では JDK を明示します。

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```

## PDT-FP1 へのインストール

USB で接続し、開発者オプションの USB デバッグを有効にしてから実行します。

```bash
./gradlew installDebug
```

APK を直接渡す場合は `app/build/outputs/apk/debug/app-debug.apk` を転送してインストールします。

## LAN 直結の手順

1. PDT-FP1 とオシロスコープを LAN ケーブルで接続します。
2. オシロスコープ前面の `Utility` ボタンを押します。
3. `Utility Page` → `I/O` を選びます。
4. `Ethernet Network Settings`（または `LAN` 設定）を開きます。
5. `Socket Server` を選び、`Enabled` を `On` にします。
6. **`Protocol` を `None` に設定します**（`Terminal` ではありません）。
7. ポート番号を確認します。初期候補は **4000** です。
8. オシロスコープの IP アドレスを確認します。
9. アプリの接続画面へ IP アドレスとポートを入力します。
10. 「接続」を押し、続けて「接続診断」で各段階を確認します。

アプリ内の接続画面「初期設定の手順」からも同じ案内を開けます。

### `Protocol` を `None` にする理由

`Terminal` モードではオシロスコープが対話用のバナー・エコー・プロンプトを返すため、
プログラムからの SCPI 応答解析に干渉します。本アプリは接続直後の未要求データを破棄し、
コマンドのエコーを検出して「`None` に設定してください」と警告しますが、
正しく動かすには `None` が必要です。

### IP 設定例

直結で DHCP が使えない場合は双方に静的 IP を設定します。以下は**設定例**で、
この値でなければならないわけではありません。同一サブネットなら任意の値で構いません。

```text
PDT-FP1:
  IP アドレス     192.168.10.1
  サブネットマスク 255.255.255.0

オシロスコープ:
  IP アドレス     192.168.10.2
  サブネットマスク 255.255.255.0

ポート:
  4000
```

詳細は [docs/network-setup.md](docs/network-setup.md) を参照してください。

## 接続診断

接続画面の「接続診断」を実行すると、次の項目を順に確認して結果と対処方法を表示します。

Ethernet 検出 → PDT-FP1 の IP・サブネット・ゲートウェイ・DNS → サブネットの一致 →
TCP ポート接続 → 経路検証（モバイル誤接続の検出）→ SCPI 応答 → `*IDN?` → 応答時間 →
モデル判定 → ファームウェア → 対応機能

## e*Scope の開き方

対応機種では、オシロスコープの IP アドレスへブラウザでアクセスすると本体画面の遠隔確認や
一部操作ができます。接続後、接続画面の「e*Scope を開く」から開けます。

```text
http://<オシロスコープの IP>/
```

アプリ内 WebView と外部ブラウザのどちらでも開けます。
**e*Scope の画面を解析して非公式 API として利用することはしていません。** 表示するだけです。

## 実機なしでの動作確認（疑似サーバー）

実機がなくても、疑似オシロスコープを起動して一通り動かせます。

```bash
./gradlew :simulator:run --args="--port 4000 --model MDO4104C --verbose"
```

同じ PC の Android エミュレータから接続する場合、エミュレータ内の `10.0.2.2` がホスト PC を指します。

```text
IP アドレス   : 10.0.2.2
ポート        : 4000
バインド方式  : システム既定（バインドなし）
```

モデルや障害注入の一覧は [docs/simulator.md](docs/simulator.md) を参照してください。

## テスト実行

```bash
./gradlew testDebugUnitTest :core:common:test :core:scpi:test :core:model:test :core:waveform:test :simulator:test
./gradlew ktlintCheck
```

UI テストは端末またはエミュレータが必要です。

```bash
./gradlew :app:connectedDebugAndroidTest
```

## モジュール構成

| モジュール | 役割 |
| --- | --- |
| `core:model` | ドメインモデル（Kotlin/JVM、Android 非依存） |
| `core:common` | 工学単位表示、エラー分類、通信ログ、ログ抽象 |
| `core:network` | Ethernet 検出、ソケットバインド、TCP Raw Socket、経路検証、診断、機器探索 |
| `core:scpi` | コマンドキュー、応答解析、エラーキュー、Capability 検出、各種コントローラ |
| `core:waveform` | プリアンブル、波形デコード、スケーリング、デシメーション |
| `core:database` | Room、波形ファイルストア、エクスポート |
| `core:ui` | Material 3 テーマと共通 Composable |
| `feature:*` | 接続 / 概要 / 波形 / チャンネル / トリガ / 測定 / オプション / 自動測定 / ファイル / コンソール / 設定 |
| `simulator` | 実機不要の疑似オシロスコープ TCP サーバー（Kotlin/JVM） |

SCPI 解析と波形演算は Android 非依存の JVM モジュールへ寄せてあります。実機もエミュレータも
無しに単体テストと統合テストを実行できます。詳細は [docs/architecture.md](docs/architecture.md)。

## 既知の制限

- **実機のオシロスコープで未検証です。** 検証済みの範囲は
  [docs/hardware-validation.md](docs/hardware-validation.md) を参照してください。
- VXI-11 は未実装です。理由と工数は [docs/vxi11-feasibility.md](docs/vxi11-feasibility.md)。
- トリガはエッジのみ完全実装です。パルス幅・ラント・ビデオなどは種別の切り替えのみで、
  詳細設定は SCPI コンソールから行います（画面上に該当コマンドを表示しています）。
- バスの詳細設定（クロック源、ビットレートなど）、Spectrogram、マーカーは未実装です。
- Math / Reference の専用画面はありません。SCPI コンソールから操作できます。
- mDNS / DNS-SD / LXI 探索は未実装です。機器探索は同一サブネットの限定走査のみです。
- Kotlin は 2.3.21 を使用しています。Room 2.8.4 が同梱する `kotlin-metadata-jvm` が
  Kotlin 2.4 のメタデータを読めないためです。

## セキュリティ上の注意

- 本アプリは計測器を遠隔操作します。信頼できるネットワークでのみ使用してください。
- Socket Server による SCPI 通信は暗号化も認証もされません。
- 接続直後は必ず読み取り専用モードです。設定変更は明示的に解除してから行います。
  拒否は UI ではなくコマンドキュー層で強制しています。
- `*RST` / `FACTORY` / `AUTOSet` / AFG 出力の有効化 / ファイル削除は確認を必須にしています。
- 本体から返るファイル名をそのままコマンドへ差し込みません。親ディレクトリ参照・引用符・
  改行・セミコロンを含む名前は拒否します。
- 保存先はアプリ専用ストレージです。ファイル名は正規化し、保存先の外へ書き込めないようにしています。

## 完了条件の達成状況

プロンプト 22 章の完了条件に対する状況です。実機が必要な項目は明示しています。

| 条件 | 状況 |
| --- | --- |
| Android Studio でビルド成功 | 達成（`assembleDebug`） |
| PDT-FP1 へインストール可能 | エミュレータで `installDebug` を確認。実機は未確認 |
| Ethernet を検出可能 | 実装済み。**実機確認が必要** |
| Ethernet へソケットをバインド可能 | 実装済み。**実機確認が必要** |
| IP とポートを指定して接続可能 | 疑似サーバーで確認済み |
| `*IDN?` を取得可能 | 疑似サーバーで確認済み |
| 実機モデルを表示可能 | 疑似サーバーで確認済み |
| 基本設定を読み出せる | 疑似サーバーで確認済み |
| Run／Stop／Single 操作が可能 | 疑似サーバーで確認済み |
| CH1 波形を取得可能 | 疑似サーバーで確認済み |
| バイナリブロックを正しく解析可能 | 単体・統合テストで確認済み |
| 波形を電圧対時間として表示可能 | UI テストで確認済み |
| CSV と PNG を保存可能 | UI テストで確認済み |
| 接続切断から復帰可能 | 統合テストで確認済み |
| 未対応コマンドでクラッシュしない | 統合テストで確認済み |
| 読み取り専用モードが機能する | 統合・UI テストで確認済み |
| SCPI コンソールが使用可能 | 実装済み |
| 疑似サーバーで自動テスト可能 | 達成 |
| README と互換性表が存在する | 達成 |

## ドキュメント

- [docs/compatibility-matrix.md](docs/compatibility-matrix.md) — モデル × 機能の対応表
- [docs/scpi-command-support.md](docs/scpi-command-support.md) — 使用コマンド一覧と出典
- [docs/network-setup.md](docs/network-setup.md) — Ethernet 直結と診断
- [docs/simulator.md](docs/simulator.md) — 疑似サーバー
- [docs/hardware-validation.md](docs/hardware-validation.md) — 実機確認が必要な項目
- [docs/vxi11-feasibility.md](docs/vxi11-feasibility.md) — VXI-11 の工数とリスク
- [docs/architecture.md](docs/architecture.md) — モジュール構成

## ライセンスと出典

Tektronix 公式 Programmer Manual の内容は本リポジトリへ転載していません。
コマンドの確認結果は [docs/scpi-command-support.md](docs/scpi-command-support.md) に
要約と出典のみ記載しています。
