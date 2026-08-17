# PDToscillo 展示PV

16:9・60秒・無音の展示用コンセプトPVです。プロダクトPVはSVG人物・機器・UIを部品化した2.5Dモーション、解説版と技術デモ版はThree.jsで描画します。

## 3つの完成版

- `PDToscillo-product-pv.mp4` — 作業員の課題とPDT-FP1による解決を描く2.5DプロダクトPV
- `PDToscillo-explainer.mp4` — 図解と文字の読みやすさを優先した展示向け解説
- `PDToscillo-technical-demo.mp4` — プロトコルとデータフローを強調した技術デモ

各動画は1920×1080、60fps、H.264、60秒、無音です。対応する16:9動画サムネイルは `assets/*-thumbnail.png`、動画とは独立した16:9プロジェクトサムネイルは `assets/project-thumbnail.png` です。

## 再生とテーマ切替

`index.html` をブラウザで開き、クエリでテーマを切り替えます。

```text
index.html?variant=product-pv
index.html?variant=explainer
index.html?variant=technical-demo
```

指定しない場合は `product-pv` を再生します。60秒で先頭に戻ります。

## プロダクトPVの絵コンテ

| 時間 | 内容 |
| --- | --- |
| 0–15秒 | 専用計測器、重いフィールドPC、RS-232、最新OSに対応しない専用ソフト |
| 15–28秒 | 高価で重く、ボタンと階層メニューが複雑なディスプレイ付き計測器 |
| 28–44秒 | 同じ専用計測器とPDT-FP1をLAN接続し、グラフ・数値・パラメーターを手元に表示 |
| 44–60秒 | 専用計測器、LXI/SCPI、Dante/AES67、GigE Visionを識別してUIを切り替える将来像 |

PDT-FP1の2.5Dモデルは、Sony公式の商品画像、仕様、ヘルプガイドを形状資料として参照し、独自のSVGとして作成しています。公式画像ファイルそのものは収録していません。

- [PDT-FP1 商品情報](https://www.sony.jp/ichigan/products/PDT-FP1/)
- [PDT-FP1 主な仕様](https://www.sony.jp/ichigan/products/PDT-FP1/spec.html)
- [本体背面・LAN端子](https://helpguide.sony.net/mobile/pdt-fp1/v1/ja/contents/part_name_rear_view_23d.html)

## MP4の再生成

FFmpeg EssentialsとPlaywrightを用意して、次を実行します。

```powershell
node .\record-animation.mjs
```

3テーマをフレームキャプチャしてMP4と動画別サムネイルを生成します。

プロジェクトサムネイルは動画フレームや生成画像を使わず、専用のHTML・CSS・インラインSVGから別途生成します。

```powershell
node .\render-project-thumbnail.mjs
```

個別に再生成する場合は、テーマ名を指定します。

```powershell
$env:PDTOSCILLO_VARIANTS = 'explainer'
node .\record-animation.mjs
```

## 表現上の区別

解説版・技術デモ版で描く現行デモは **Tektronix 4000系オシロスコープをSCPI Raw Socketで扱う操作イメージ** です。プロダクトPVの専用計測器接続には「利用コンセプト」を表示します。

LXI/SCPI系計測器、Dante/AES67音響機器、GigE VisionカメラなどをLAN接続時に識別し、最適なUIを表示する機能は **将来ビジョン** として明示しています。物理的にLANを使う機器でも上位プロトコルは同一ではないため、規格別アダプターで段階的に対応範囲を広げる構想です。
