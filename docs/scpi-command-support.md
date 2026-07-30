# SCPI コマンド対応表

本アプリが送信する SCPI コマンドの一覧です。

## 原則

1. **存在を確認できていないコマンドは実装しない。** 全てのコマンドは Tektronix 公式 Programmer Manual の
   記載を確認した上で追加します。
2. 出典欄は確認に使ったマニュアルを示します。**マニュアル本文は転載しません。**
3. 「Capability」欄は、そのコマンドを有効化する条件です。条件を満たさない場合 UI 上で無効化します。
4. 設定変更コマンド（Set）と問い合わせ（Query）を明確に区別します。

## 出典

| 略称 | マニュアル |
| --- | --- |
| **M4K-BC** | MDO4000C, MDO4000B, MDO4000, MSO4000B, DPO4000B and MDO3000 Series Oscilloscopes Programmer Manual (077-0510-07) |
| **M4K** | MSO4000 and DPO4000 Series Digital Phosphor Oscilloscopes Programmer Manual (077-0248-01) |

いずれも Tektronix の公式サイト（`www.tek.com/manuals`）から入手できます。

## Phase 1: 識別

| コマンド | 種別 | 応答形式 | Capability | 出典 |
| --- | --- | --- | --- | --- |
| `*IDN?` | Query | `TEKTRONIX,<model>,<serial>,<firmware>` のカンマ区切り文字列 | 常時 | M4K-BC / M4K |
| `*ESR?` | Query | 整数（Standard Event Status Register） | 常時 | M4K-BC / M4K |
| `EVMsg?` | Query | `<event code>,"<message>"` | 常時 | M4K-BC / M4K |
| `ALLEv?` | Query | 複数イベントのカンマ区切り | 常時 | M4K-BC / M4K |
| `EVQty?` | Query | キュー内イベント数（整数） | 常時 | M4K-BC |
| `*OPC?` | Query | `1`（処理完了） | 常時 | M4K-BC / M4K |
| `BUSY?` | Query | `0` / `1` | 常時 | M4K-BC / M4K |

`*IDN?` の応答は「メーカー, モデル, シリアル番号, ファームウェア」の 4 要素を想定しますが、
要素数が異なる機種でも例外を出さず、取得できた分だけを表示します。

## Phase 2 以降

Phase の進行に合わせて追記します。現時点で確認済みのコマンドグループは次のとおりです（M4K-BC）。

Acquisition / Act on Event / AFG / Alias / ARB / Bus / Calibration and Diagnostic / **Configuration** /
Cursor / Display / DVM / Email / Ethernet / File System / Hard Copy / Histogram / Horizontal / Mark /
Mask / Math / Measurement / Miscellaneous / PictBridge / Power / RF / Save and Recall / Search /
Status and Error / Trigger / Vertical / Video Picture / Waveform Transfer / Zoom

### Configuration グループ（Capability 検出の主経路）

`CONFIGuration:*?` は**本体設定を変更せずに**機能の有無を問い合わせられるクエリ群です。
本アプリはこれを Capability 検出の第一手段とします（M4K-BC）。

| クエリ | 取得内容 |
| --- | --- |
| `CONFIGuration:ANALOg:NUMCHANnels?` | アナログチャンネル数 |
| `CONFIGuration:ANALOg:MAXBANDWidth?` | アナログ最大帯域 |
| `CONFIGuration:ANALOg:MAXSAMPLERate?` | アナログ最大サンプルレート |
| `CONFIGuration:ANALOg:RECLENS?` | 対応レコード長の一覧 |
| `CONFIGuration:ANALOg:VERTINVert?` | 反転機能の有無 |
| `CONFIGuration:DIGITAl:NUMCHANnels?` | デジタルチャンネル数 |
| `CONFIGuration:DIGITAl:MAGnivu?` | MagniVu の有無 |
| `CONFIGuration:RF:NUMCHANnels?` | RF チャンネル数 |
| `CONFIGuration:RF:BANDWidth?` | RF 帯域 |
| `CONFIGuration:RF:ADVTRIG?` | RF 高度トリガの有無 |
| `CONFIGuration:AFG?` | AFG ハードウェアと機能の有無 |
| `CONFIGuration:ARB?` | 任意波形機能の有無 |
| `CONFIGuration:DVM?` | DVM の有無 |
| `CONFIGuration:NUMMEAS?` | 同時測定数 |
| `CONFIGuration:REFS:NUMREFS?` | Reference 波形の数 |
| `CONFIGuration:ADVMATH?` | 拡張 Math の有無 |
| `CONFIGuration:BUSWAVEFORMS:NUMBUS?` | バス波形数 |
| `CONFIGuration:BUSWAVEFORMS:I2C?` 他 | 各バスデコードオプションの有無 |
| `CONFIGuration:HISTOGRAM?` | 波形ヒストグラムの有無 |
| `CONFIGuration:EXTVIDEO?` | 拡張ビデオトリガの有無 |
| `CONFIGuration:AUXIN?` | Aux 入力コネクタの有無 |
| `CONFIGuration:ROSC?` | 外部基準発振器入力の有無 |

> **注意**: 無印世代（DPO4000 / MSO4000）にこのグループが無い可能性があります。
> 未定義ヘッダーを検出した場合はモデル名からの推定へフォールバックします。
> 詳細は [compatibility-matrix.md](compatibility-matrix.md) を参照。

### Waveform Transfer グループ（Phase 3 で実装）

M4K-BC で確認した内容です。

| コマンド | 種別 | 引数 / 応答 |
| --- | --- | --- |
| `DATa:SOUrce` | Set / Query | `CH1`–`CH4`, `MATH`, `REF1`–`REF4`, `D0`–`D15`, `DIGital`, `RF_AMPlitude`, `RF_FREQuency`, `RF_PHASe`, `RF_NORMal`, `RF_AVErage`, `RF_MAXHold`, `RF_MINHold` |
| `DATa:STARt` | Set / Query | 開始点（NR1） |
| `DATa:STOP` | Set / Query | 終了点（NR1） |
| `DATa:ENCdg` | Set / Query | `ASCIi`, `FAStest`, `RIBinary`, `RPBinary`, `SRIbinary`, `SRPbinary`, `FPbinary`, `SFPbinary`（既定 `RIBINARY`） |
| `DATa:WIDth` | Set / Query | 1 点あたりのバイト数 |
| `WFMOutpre?` | Query | プリアンブル一括。`BYT_Nr`, `BIT_Nr`, `ENCdg`, `BN_Fmt`, `BYT_Or`, `NR_Pt`, `XUNit`, `XINcr`, `XZEro`, `PT_Off`, `YUNit`, `YMUlt`, `YOFf`, `YZEro` |
| `CURVe?` | Query | ASCII はカンマ区切り、バイナリは IEEE 488.2 ブロック |

`DATa:ENCdg` は `WFMOutpre:ENCdg` / `BN_Fmt` / `BYT_Or` を同時に設定します。個別指定も可能ですが、
本アプリは `DATa:ENCdg` で設定し、実際に適用された値を `WFMOutpre?` で読み戻して使用します。

補足（M4K-BC 記載）:

- ASCII で `CURVe?` から取得できるのは最大 100 万点。それを超える場合はバイナリ必須。
- RF 周波数領域データは 4 バイト浮動小数。
- Digital Collection は 1 点あたり 4 または 8 バイトの 16 進値。
- 波形が表示されていない状態で問い合わせるとエラーになる。

### スケーリング式（M4K-BC 記載）

```text
Xn = XZEro + XINcr (n - PT_Off)
Yn = YZEro + YMUlt (yn - YOFf)
```

Envelope / Peak Detect のように 1 点が最大・最小の対になる場合は次の形になります。

```text
Ynmax = YZEro + YMUlt (ynmax - YOFf)
Ynmin = YZEro + YMUlt (ynmin - YOFf)
```

### IEEE 488.2 definite-length block

```text
#<桁数><データ長><バイナリデータ>
```

M4K-BC の例では `#510000` に続いて 10,000 バイトが送られます（桁数 `5`、長さ `10000`）。
