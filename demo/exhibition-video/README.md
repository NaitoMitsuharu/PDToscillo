# PDToscillo 展示PV

Three.jsで描画する、16:9・60秒・無音の展示用コンセプトPVです。スライドの切替ではなく、同一の仮想実験室をカメラが移動し、PC、計測器、PDT-FP1、LANのデータ、将来対応機器が連続して変化します。

## 3つの完成版

- `PDToscillo-product-pv.mp4` — 暗めの実験室と発光LANによるプロダクトPV
- `PDToscillo-explainer.mp4` — 図解と文字の読みやすさを優先した展示向け解説
- `PDToscillo-technical-demo.mp4` — プロトコルとデータフローを強調した技術デモ

各動画は1920×1080、H.264、60秒、無音です。対応する16:9サムネイルは `assets/*-thumbnail.png`、共通の正方形プロジェクトサムネイルは `assets/project-thumbnail.png` です。

## 再生とテーマ切替

`index.html` をブラウザで開き、クエリでテーマを切り替えます。

```text
index.html?variant=product-pv
index.html?variant=explainer
index.html?variant=technical-demo
```

指定しない場合は `product-pv` を再生します。60秒で先頭に戻ります。

## MP4の再生成

FFmpeg EssentialsとPlaywrightを用意して、次を実行します。

```powershell
node .\record-animation.mjs
```

3テーマをフレームキャプチャしてMP4と動画別サムネイルを生成します。

個別に再生成する場合は、テーマ名を指定します。

```powershell
$env:PDTOSCILLO_VARIANTS = 'explainer'
node .\record-animation.mjs
```

## 表現上の区別

現行デモで描くのは **Tektronix 4000系オシロスコープをSCPI Raw Socketで扱う操作イメージ** です。

LXI/SCPI系計測器、Dante/AES67音響機器、GigE VisionカメラなどをLAN接続時に識別し、最適なUIを表示する機能は **将来ビジョン** として明示しています。物理的にLANを使う機器でも上位プロトコルは同一ではないため、規格別アダプターで段階的に対応範囲を広げる構想です。
