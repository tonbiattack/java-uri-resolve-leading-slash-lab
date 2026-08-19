# 題材企画: `URI.resolve`の先頭スラッシュでAPIバージョンのパスを失う

## 対象

| 項目 | 内容 |
| --- | --- |
| 対象言語 | Java 21 |
| 対象読者 | `java.net.URI`で設定済みの基底URIとエンドポイント相対参照を結合する中級者 |
| 難易度プロファイル | 実践・上級 |
| 選定理由 | `URI.resolve`は階層URIの参照を基底URIに対して解決する。先頭スラッシュを持つ参照は基底パスの子ではなく絶対パスであり、`/v1/`のような基底パスを置き換える。直接の解決URI、最後の採用URI、採用件数を分けて観測し、基底URIの末尾スラッシュ・参照の先頭スラッシュ・`resolve`の規則を比較できる。 |
| 実行基盤 | Maven、Java 21、JUnit Jupiter 5.11.4 |
| フレームワーク非依存性 | 原因は`java.net.URI.resolve`の標準ライブラリ契約である。HTTPクライアント、DI、DB、外部ネットワーク、フレームワークには依存しない。 |

## 学習する契約

> 基底URI`https://api.example.test/v1/`で、先に`health`を正常にルーティングした状態から、請求書ID`42`をルーティングする場合、`https://api.example.test/v1/invoices/42`を採用し、採用件数を二件にすべきだが、バグ状態では`https://api.example.test/invoices/42`に解決されて`OUTSIDE_API_VERSION`となり、先のhealth URIと採用件数一件が残る。

### 対象の直接原因

請求書参照を`"/invoices/" + invoiceId`として作り、`baseUri.resolve(reference)`へ渡している。先頭スラッシュを持つ階層URIのパスは絶対パスであるため、`/v1/`の下の相対参照ではなく、ホスト直下の`/invoices/42`として解決される。

### 対象外

このラボはネットワーク通信、認証、クエリパラメータ、パーセントエンコード、リダイレクト、DNS、プロキシ、パストラバーサル、利用者入力のURI検証を扱わない。固定の基底URIと固定の請求書IDから、同じAPIバージョンのパスに解決する狭い規則だけを扱う。

## 再現設計

| 要素 | 決定 |
| --- | --- |
| 公開境界 | `PartnerApiUriRouter#routeHealth()`、`routeInvoice(String)`、`lastAcceptedUri()`、`acceptedRouteCount()`。 |
| 入力・初期状態 | 基底URIを`https://api.example.test/v1/`とし、最初に`health`を成功ルーティング後、請求書ID`42`をルーティングする。 |
| Redの観測 | `RouteOutcome.ROUTED_TO_API_VERSION`を期待するが、バグ状態では`RouteOutcome.OUTSIDE_API_VERSION`となる。 |
| 最終観測 | `lastAcceptedUri()`が`https://api.example.test/v1/invoices/42`となり、`acceptedRouteCount()`が`2`であることを別々に検証する。 |
| 決定性 | 時刻、乱数、並行実行、`sleep`、外部I/Oを使わず、固定のURI文字列とインメモリ状態だけを使う。 |
| 固定状態の検証コマンド | `mvn --batch-mode clean test` |
| バグ状態の確認コマンド | `git checkout <bug-commit>`後に`mvn --batch-mode test -Dtest=PartnerApiUriRouterTest` |

## 仮説

| 仮説 | どう検証または除外するか |
| --- | --- |
| A: 基底URIに末尾スラッシュがないため最後のパス要素がファイルとして扱われている | 末尾スラッシュを持つ固定基底URIで、`health`の相対参照が`/v1/health`へ解決することを確認する。 |
| B: 請求書IDの連結が壊れている | `42`が参照・解決URIの末尾に保持されることを直接観測する。 |
| C: 先頭スラッシュを持つ参照が基底パスを置き換える | 同じ基底URIに`invoices/42`と`/invoices/42`をそれぞれresolveし、得られるパスを比較する。 |

## 予定する履歴

| 順序 | コミットの目的 | 期待する状態 |
| --- | --- | --- |
| 1 | APIバージョンのパスを失うURI解決失敗を再現する | 対象テストが`ROUTED_TO_API_VERSION`期待・`OUTSIDE_API_VERSION`実際のアサーション差分で失敗する。 |
| 2 | APIバージョンの下で請求書URIを解決する | 同じ検証が成功し、全体も成功する。 |
