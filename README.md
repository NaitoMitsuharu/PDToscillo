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

PDT-FP1 では Ethernet・Wi-Fi・モバイル回線が同時に有効になり得ます。本アプリはソケットを Ethernet の
`Network` へ明示的にバインドし、モバイル回線へ誤って接続しないことを接続後に検証します。

## 状態

実装の進行状況です。**実機は未接続**のため、検証済みの範囲は
[docs/hardware-validation.md](docs/hardware-validation.md) で「自動テスト済み」と
「実機確認が必要」に分けて記載しています。

- [x] Phase 0: マルチモジュール構成、ビルド基盤、静的解析
- [x] Phase 1: Ethernet 検出・ソケットバインド・TCP 接続・`*IDN?`・接続診断・疑似サーバー
- [ ] Phase 2: SCPI コマンドキュー・エラー処理・Capability 検出・チャンネル設定
- [ ] Phase 3: 波形転送・バイナリブロック解析・Compose 描画・CSV/PNG 保存
- [ ] Phase 4: 水平軸・Acquisition・Trigger・Measurement・Cursor・Math/Reference
- [ ] Phase 5: デジタル/バス・Spectrum・AFG・DVM・ファイル操作・SCPI コンソール
- [ ] Phase 6: 自動測定・性能改善・ドキュメント

## 対応予定モデル

ユーザーから得られている情報は「Tektronix 4000 シリーズ」のみで、正確なモデルは未確定です。
特定モデル専用の実装はせず、接続後に `*IDN?` で識別し、対応機能だけを有効化します。

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

## テスト実行

```bash
./gradlew testDebugUnitTest ktlintCheck
```

## モジュール構成

| モジュール | 役割 |
| --- | --- |
| `core:model` | ドメインモデル（Kotlin/JVM、Android 非依存） |
| `core:common` | 工学単位表示、エラー分類、ログ抽象 |
| `core:network` | Ethernet 検出、ソケットバインド、TCP Raw Socket トランスポート |
| `core:scpi` | SCPI コマンドキュー、応答解析、Capability 検出、Tektronix ドライバ |
| `core:waveform` | プリアンブル解析、波形デコード、スケーリング、デシメーション |
| `core:database` | Room、DataStore、波形ファイルストア |
| `core:ui` | Material 3 テーマと共通 Composable |
| `feature:*` | 接続 / 概要 / 波形 / 測定 / 自動測定 / ファイル / 設定 / コンソール |
| `simulator` | 実機不要の疑似オシロスコープ TCP サーバー（Kotlin/JVM） |

SCPI 解析と波形演算は Android 非依存の JVM モジュールへ寄せてあります。実機もエミュレータも無しに
単体テストと統合テストを実行できます。

## セキュリティ上の注意

- 本アプリは計測器を遠隔操作します。信頼できるネットワークでのみ使用してください。
- Socket Server による SCPI 通信は暗号化も認証もされません。
- 接続直後は読み取り専用モードです。設定変更は明示的に解除してから行います。

## ライセンスと出典

Tektronix 公式 Programmer Manual の内容は本リポジトリへ転載していません。
コマンドの確認結果は [docs/scpi-command-support.md](docs/scpi-command-support.md) に要約と出典のみ記載します。
