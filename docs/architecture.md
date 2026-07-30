# アーキテクチャ

## 依存方向

```text
                    ┌──────┐
                    │ :app │
                    └───┬──┘
        ┌───────────────┼───────────────┐
        ▼               ▼               ▼
  feature:connection  feature:waveform  feature:… （相互依存なし）
        └───────────────┼───────────────┘
                        ▼
        ┌──────────┬──────────┬──────────┬──────────┐
        ▼          ▼          ▼          ▼          ▼
   core:network core:scpi core:waveform core:database core:ui
        └──────────┴────┬─────┴──────────┴──────────┘
                        ▼
                  core:common
                        ▼
                   core:model
```

- `feature` 同士は依存しません。画面間の遷移は `:app` の Navigation が担います。
- `core:model` と `core:common`、`core:scpi`、`core:waveform` は **Kotlin/JVM モジュール**です。
  Android に依存しないため、実機やエミュレータなしで高速に単体テストできます。
- `core:network`、`core:database`、`core:ui`、`feature` は Android モジュールです。
- `:simulator` はアプリから参照されません。`core:network` と `core:scpi` の
  **テスト依存**としてのみ使います。

## なぜ SCPI と波形処理を JVM モジュールへ置くか

このアプリの正しさは「SCPI 応答の解析」と「波形のデコードとスケーリング」に集約されます。
これらを Android に依存させないことで次が成立します。

- `./gradlew :core:scpi:test` だけで解析ロジックを検証できる
- 疑似サーバーへ実際に TCP 接続する統合テストを JVM 単体テストとして書ける
- TCP のパケット境界と SCPI 応答境界が一致しない状況を再現できる

## 層の責務

| モジュール | 責務 | 置かないもの |
| --- | --- | --- |
| `core:model` | ドメインモデル、`InstrumentTransport` インターフェース、列挙型 | 実装ロジック |
| `core:common` | 工学単位の整形、エラー分類、ログ抽象 | Android API |
| `core:network` | Ethernet 検出、ソケットのネットワークバインド、Raw Socket 実装、経路検証 | SCPI の意味づけ |
| `core:scpi` | コマンドキュー、直列化、応答解析、エラーキュー、Capability 検出、Tektronix ドライバ | ソケットの詳細、UI 状態 |
| `core:waveform` | プリアンブル解析、バイナリデコード、スケーリング、デシメーション | 描画 |
| `core:database` | Room、DataStore、波形ファイルストア | ドメインロジック |
| `core:ui` | テーマ、共通 Composable、単位付き数値入力 | 機器通信 |
| `feature:*` | 画面と ViewModel | SCPI コマンド文字列の直書き |
| `:simulator` | 疑似オシロスコープ TCP サーバーと障害注入 | アプリ側コードの再利用 |

## 通信の直列化

1 接続あたり 1 本のコマンドキューを持ちます。Query の応答を読み切る前に別の Query を送りません。
これはオシロスコープ側が単一のコマンド解釈器を持つためで、並行送信すると応答の対応関係が崩れます。

キャンセル時は受信ストリームを壊さないよう、読み切れなかった応答があれば同期喪失として扱い、
必要なら再接続します。

## 読み取り専用モードの強制点

読み取り専用モードは UI ではなく**コマンドキュー層**で強制します。
UI 側の確認漏れがあっても、設定変更コマンドはキューで拒否されます。
