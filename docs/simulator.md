# 疑似オシロスコープ

実機がなくても開発とテストを進めるための TCP サーバーです。Kotlin/JVM で動きます。

**アプリ側のコードを一切参照していません。** 同じコードで作って同じコードで解析すると、
双方が同じ誤解をしていてもテストが通ってしまうためです。

## 起動

```bash
./gradlew :simulator:run --args="--port 4000 --model MDO4104C --verbose"
```

同じ PC の Android エミュレータから接続する場合、エミュレータ内の `10.0.2.2` がホスト PC を指します。

```text
IP アドレス: 10.0.2.2
ポート      : 4000
バインド方式: システム既定（バインドなし）
```

## オプション

| オプション | 内容 |
| --- | --- |
| `--port <番号>` | 待受ポート（既定 4000、`0` で自動割当） |
| `--model <名前>` | 応答するモデル |
| `--fault <モード>` | 障害注入 |
| `--shape <形状>` | 生成する波形（`SINE` / `SQUARE` / `NOISE` / `PULSE`） |
| `--terminal` | `Protocol: Terminal` を模してバナー・エコー・プロンプトを返す |
| `--verbose` | 受信コマンドを表示 |

## モデル

世代差による挙動の違いを再現します。特に `CONFIGuration:*?` の有無が重要です。

| モデル | アナログ | デジタル | RF | AFG | DVM | `CONFIGuration:*?` |
| --- | --- | --- | --- | --- | --- | --- |
| `DPO4032` | 2 | 0 | × | × | × | 非対応 |
| `DPO4054` | 4 | 0 | × | × | × | 非対応 |
| `MSO4104` | 4 | 16 | × | × | × | 非対応 |
| `MSO4104B` | 4 | 16 | × | ○ | ○ | 対応 |
| `MDO4104C` | 4 | 16 | ○ | ○ | ○ | 対応 |

`CONFIGuration:*?` 非対応のモデルでは、実機と同じく**応答を返さず**イベント 113
（Undefined header）をキューへ積みます。これにより、アプリ側のフォールバック経路を検証できます。

## 障害注入

実機で起こり得る、受信側の実装を誤らせやすい状況を意図的に作ります。

| モード | 内容 | 何を検証するか |
| --- | --- | --- |
| `NONE` | 正常応答 | 基本動作 |
| `SPLIT_RESPONSE` | 応答を細かく分割して送る | **TCP のパケット境界と SCPI の応答境界は一致しない。** 1 回の read で揃う前提の実装はここで壊れる |
| `DELAYED_RESPONSE` | 応答を遅らせる | タイムアウト設定と、遅延応答を次の応答と取り違えないこと |
| `NO_RESPONSE` | 応答を返さない | 読み取りタイムアウトの分類 |
| `DISCONNECT_MIDWAY` | 応答の途中で切断 | 不完全な応答を成功として扱わないこと |
| `BAD_BLOCK_LENGTH` | ブロックのヘッダ長と実データ長を食い違わせる | 宣言長を信じて読み切ろうとした場合の検出 |
| `HUGE_BLOCK_LENGTH` | 極端に大きい長さを宣言 | 上限チェック（メモリを確保しないこと） |
| `UNSUPPORTED_COMMAND` | すべてを未定義として扱う | 未対応機種でクラッシュしないこと |
| `BUSY` | `BUSY?` が 1 を返し設定変更を受け付けない | Busy 状態の扱い |

## 対応しているコマンド

アプリが送るコマンドに対応しています。主なもの:

- `*IDN?`, `*ESR?`, `EVMsg?`, `ALLEv?`, `EVQty?`, `*OPC?`, `BUSY?`, `*CLS`, `HEADer`
- `CONFIGuration:*?`（対応モデルのみ）
- `ACQuire:STATE` / `STOPAfter` / `MODe` / `NUMAVg`
- `HORizontal:SCAle` / `POSition` / `RECOrdlength` / `SAMPLERate?`
- `SELect:CH<x>`, `CH<x>:SCAle` / `POSition` / `OFFSet` / `COUPling` / `BANdwidth` / `INVert` /
  `LABel` / `TERmination` / `DESKew` / `PRObe:GAIN`
- `TRIGger:A:TYPe` / `EDGE:*` / `LEVel`, `TRIGger:STATE?`, `TRIGger FORCe`
- `DATa:SOUrce` / `STARt` / `STOP` / `ENCdg` / `WIDth`, `WFMOutpre?`, `CURVe?`
- `MEASUrement:*`

未知のコマンドには、実機と同じく**応答を返さず**イベント 113 を積みます。

## 波形とプリアンブル

`WFMOutpre?` のフィールドの並びは、公式 Programmer Manual の例と同じ順序にしています。

```text
BYT_NR, BIT_NR, ENCDG, BN_FMT, BYT_OR, WFID, NR_PT, PT_FMT,
XUNIT, XINCR, XZERO, PT_OFF, YUNIT, YMULT, YOFF, YZERO
```

`HEADer` の設定でヘッダ有無が切り替わります。アプリ側はどちらの形式でも解析できる必要があるため、
既定はヘッダ有効（マニュアルの例と同じ形）にしています。

## 統合テストからの利用

`ScopeSimulator` はポート 0 で起動して実際に割り当てられたポートを返します。
テストからは `SimulatorHarness` 経由で使います。

```kotlin
SimulatorHarness(model = SimulatedModel.MDO4104C, faultMode = FaultMode.SPLIT_RESPONSE).use { harness ->
    harness.client.connect(harness.config())
    val identity = harness.client.identify()
}
```

実時間の I/O を扱うため、テストは `runTest`（仮想時間）ではなく `runBlocking` を使います。
仮想時間ではタイムアウトが即座に発火し、実際の挙動を検証できません。
