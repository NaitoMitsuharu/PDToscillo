# ネットワーク設定

## 1. 物理接続

PDT-FP1 の本体 LAN 端子とオシロスコープの LAN 端子を LAN ケーブルで直結します。
PDT-FP1 の LAN 端子は 10BASE-T / 100BASE-TX / 1000BASE-T に対応しています。

ハブやスイッチを経由しても動作しますが、本アプリは**直結を最優先**の構成として設計しています。

## 2. オシロスコープ側の設定

機種により画面構成が異なりますが、おおむね次の手順です。

1. 前面の `Utility` ボタンを押す
2. `Utility Page` → `I/O` を選ぶ
3. `Ethernet Network Settings` または `LAN` を開く
4. `Socket Server` を選ぶ
5. `Enabled` を `On` にする
6. `Protocol` を **`None`** にする
7. `Port` を確認する（初期候補は **4000**）
8. `Ethernet Network Settings` でオシロスコープの IP アドレスを確認する

### Protocol を `None` にする理由

`Terminal` モードではオシロスコープが対話用のプロンプトやエコーを返すため、
プログラムからの SCPI 応答解析に干渉します。本アプリは `None` を前提としますが、
`Terminal` モードで接続された場合も検出して画面上に警告を出します。

## 3. IP アドレス

### DHCP が使える場合

オシロスコープ側で DHCP を有効にし、割り当てられた IP アドレスを画面で確認します。

### 直結で DHCP サーバーが無い場合

双方に静的 IP を設定します。以下は**設定例**であり、この値でなければならないわけではありません。

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

同一サブネットであれば任意の値で構いません。デフォルトゲートウェイは直結では不要です。

Android 側の静的 IP は `設定` → `ネットワークとインターネット` → `イーサネット` から設定します
（端末やビルドにより項目名が異なる場合があります）。

## 4. Ethernet へのソケットバインド

PDT-FP1 では Ethernet・Wi-Fi・モバイル回線が同時に有効になり得ます。
Android の既定ルートはインターネット到達性のあるネットワークになるため、
**何もしないと直結した Ethernet ではなくモバイル回線へ接続を試みます。**
LAN 直結ではインターネット到達性が無く、Ethernet が既定ルートに選ばれないためです。

本アプリは次の手順でこれを回避します。

1. `ConnectivityManager.registerNetworkCallback` に `TRANSPORT_ETHERNET` の
   `NetworkRequest` を登録し、Ethernet の `Network` を取得する
   （インターネット到達性を要求しないリクエストを使う）
2. 取得できない場合は既存ネットワークを走査して `TRANSPORT_ETHERNET` を探す
3. `network.socketFactory.createSocket()` でソケットを生成する
   （代替として `network.bindSocket(socket)` も選択できる）
4. 接続後、ソケットのローカルアドレスが Ethernet の `LinkProperties` の
   `linkAddresses` に含まれることを検証する
5. 含まれない場合は「モバイル回線へ誤ルーティングされている可能性」として警告する

必要な権限は次の 2 つだけです。位置情報権限は要求しません。

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## 5. 接続診断

アプリの接続画面から診断を実行すると、次の項目を順に確認して結果を表示します。

| 項目 | 内容 |
| --- | --- |
| Ethernet 検出 | `TRANSPORT_ETHERNET` のネットワークが存在するか |
| PDT-FP1 の IP | Ethernet インターフェースに割り当てられたアドレス |
| サブネットマスク | プレフィックス長から算出 |
| デフォルトゲートウェイ | `LinkProperties` のルート情報 |
| DNS | `LinkProperties` の DNS サーバー |
| 対象 IP への到達性 | 指定した IP へ到達できるか |
| TCP ポート接続 | 指定ポートへ TCP 接続できるか |
| 経路検証 | ソケットが Ethernet 経由か（モバイル誤接続の検出） |
| SCPI 応答 | 応答が返るか |
| `*IDN?` 結果 | メーカー / モデル / シリアル / ファームウェア |
| 応答時間 | 往復時間 |
| Terminal モード検出 | プロンプトやエコーの有無 |
| 最後のエラー | 直近の失敗内容と推奨する対処 |

## 6. e*Scope（LXI Web UI）

対応機種では、オシロスコープの IP アドレスへブラウザでアクセスすると本体画面の遠隔確認や
一部操作ができます。本アプリからは WebView または外部ブラウザで開けます。

```text
http://<オシロスコープの IP>/
```

ネイティブ UI に未実装の操作の補助や、ネイティブ実装前の疎通確認に使えます。
**e*Scope の画面を解析して非公式 API として利用することはしません。**

## 7. うまく繋がらないとき

| 症状 | 確認事項 |
| --- | --- |
| Ethernet が検出されない | ケーブルの接続、端末の Ethernet 設定、LAN 端子の対応状況 |
| 到達性が無い | 双方が同一サブネットか、静的 IP の設定内容 |
| TCP 接続が拒否される | Socket Server が `On` か、ポート番号が一致しているか |
| 接続はできるが応答が無い | `Protocol` が `None` か、`Terminal` になっていないか |
| 応答が壊れている | `Terminal` モードのエコー、または他アプリが同時接続していないか |
| モバイル経由と警告される | Ethernet バインドを有効にする。Wi-Fi を切って再試行する |
