# E006: `URI.resolve`の先頭スラッシュがAPIバージョンのパスを置き換える

## 目的

パートナーAPIの基底URIは`https://api.example.test/v1/`です。`health`をルーティング済みの状態で請求書ID`42`をルーティングする場合、`https://api.example.test/v1/invoices/42`を採用し、採用件数を二件にする必要があります。

## 実行環境と再現境界

このラボはJava 21、Maven、JUnit Jupiter 5.11.4だけを使います。HTTPクライアント、ネットワーク、ファイル、データベース、フレームワークを使いません。公開境界は`PartnerApiUriRouter#routeHealth()`と`routeInvoice(String)`であり、直接結果として`RouteOutcome`を、最終状態として`lastAcceptedUri()`と`acceptedRouteCount()`を別々に読みます。

テストは、先に`health`の相対参照を成功させたあと、請求書参照を解決します。これにより、請求書のルートが不採用になったとき、結果コードだけでなく、最後に採用したURIと採用件数が更新されないことを確認できます。基底URI、参照、IDは固定であり、時刻、乱数、外部I/O、並行処理に依存しません。

## 最初に観測した事実

バグ状態はコミット[`254a582`](../commit/254a582)です。次のコマンドで、意図したアサーション差分を確認しました。

```bash
git checkout 254a582
mvn --batch-mode test -Dtest=PartnerApiUriRouterTest
```

| 観測項目 | 期待 | 実際 | 根拠 |
| --- | --- | --- | --- |
| 直接結果 | `ROUTED_TO_API_VERSION` | `OUTSIDE_API_VERSION` | `PartnerApiUriRouterTest` |
| 最後に採用したURI | `https://api.example.test/v1/invoices/42` | `https://api.example.test/v1/health` | `PartnerApiUriRouter#lastAcceptedUri()` |
| 採用件数 | `2` | `1` | `PartnerApiUriRouter#acceptedRouteCount()` |
| 相対参照の解決 | `https://api.example.test/v1/invoices/42` | `https://api.example.test/v1/invoices/42` | `UriResolveObservationTest` |
| 先頭スラッシュ付き参照の解決 | APIバージョン外になる | `https://api.example.test/invoices/42` | `UriResolveObservationTest` |

```text
請求書URIをAPIバージョン配下としてルーティングする
==> expected: <ROUTED_TO_API_VERSION> but was: <OUTSIDE_API_VERSION>

最後に採用したURIは/v1/の下に請求書IDを持つ
==> expected: <https://api.example.test/v1/invoices/42>
but was: <https://api.example.test/v1/health>

healthと請求書URIの二件を採用する
==> expected: <2> but was: <1>
```

完全な失敗出力は[`evidence/01-bug-service-test-output.txt`](../evidence/01-bug-service-test-output.txt)に保存しています。直接結果だけでなく、最後に採用したURIと採用件数を最終状態として分けて確認したため、表示だけの問題ではなく、請求書URIが採用処理まで到達していないことを確定できます。

## 競合仮説と検証

| 仮説 | 確認方法 | 結果 |
| --- | --- | --- |
| 基底URIの末尾スラッシュがないため`v1`がファイルのように扱われている | 末尾スラッシュを持つ基底URIで、`health`が`/v1/health`へ解決することを確認する | `health`は正しく`/v1/health`へ解決されるため棄却。 |
| 請求書IDの連結が壊れている | 相対参照・先頭スラッシュ付き参照の両方に`42`が含まれることを確認する | どちらも末尾に`42`を持つため棄却。 |
| 先頭スラッシュを持つ参照が基底パスを置き換える | 同じ基底URIで`invoices/42`と`/invoices/42`を直接比較する | 前者は`/v1/invoices/42`、後者は`/invoices/42`。採用。 |

## 確定した原因

バグ状態の`routeInvoice`は、先頭スラッシュ付きの参照を作っていました。

```java
return acceptIfInsideApiVersion(baseUri.resolve("/invoices/" + invoiceId));
```

階層URIで先頭スラッシュを持つパスは絶対パスです。`URI.resolve`は、相対参照のパスを基底URIのパスに対して解決する一方、絶対パスを持つ参照では基底パスを使いません。[1] そのため基底URIが`https://api.example.test/v1/`でも、解決結果は`https://api.example.test/invoices/42`です。

`PartnerApiUriRouter`は候補URIのパスが`/v1/`で始まることを採用条件にしているため、`/invoices/42`は`OUTSIDE_API_VERSION`として拒否されます。拒否判定は症状の表現であり、直接原因は先頭スラッシュで参照を絶対パスにしていたことです。

## 最小修正

修正コミットは[`4af0242`](../commit/4af0242)です。請求書参照から先頭スラッシュを取り除きました。

```java
return acceptIfInsideApiVersion(baseUri.resolve("invoices/" + invoiceId));
```

`"invoices/42"`は基底URI`https://api.example.test/v1/`に対する相対参照です。そのため解決結果は`https://api.example.test/v1/invoices/42`となります。[1]

基底URIへ`"/"`を連結する、文字列の`replace`で`/v1`を強制的に差し込む、テスト期待値をホスト直下へ下げる修正は採用しませんでした。今回の公開契約は、既に設定済みの基底パスの下にリソースを解決することであり、参照の表現だけを最小変更するのが適切です。

## 回帰保証

### 再発防止テスト

最初に失敗した`invoiceRoute_keepsTheApiVersionPathAndUpdatesAcceptedState`はそのまま残しています。このテストは、直接のルート結果、最後に採用したURI、採用件数を別々に検証します。

| テスト | 回帰として守る契約 |
| --- | --- |
| `invoiceRoute_keepsTheApiVersionPathAndUpdatesAcceptedState` | 請求書参照を`/v1/`の下へ解決して採用し、最後のURI・採用件数を更新する。 |
| `healthRoute_withRelativeReferenceRemainsInsideApiVersion` | 既存のhealth相対参照を従来どおり`/v1/health`へ解決する。 |
| `leadingSlashReplacesTheBasePathWhileRelativeReferenceExtendsIt` | 相対参照と先頭スラッシュ付き参照が異なる解決規則を持つことを直接示す。 |

修正後の`mvn --batch-mode clean test`では、3テストがすべて成功しました。完全な出力は[`evidence/03-fixed-full-test-output.txt`](../evidence/03-fixed-full-test-output.txt)に保存しています。

## 再現手順

```bash
git checkout 254a582
mvn --batch-mode test -Dtest=PartnerApiUriRouterTest
# expected: <ROUTED_TO_API_VERSION> but was: <OUTSIDE_API_VERSION>

git checkout main
mvn --batch-mode clean test
# Tests run: 3, Failures: 0, Errors: 0
```

## スコープと注意点

この修正は、参照を基底URIのパス配下へ解決する場合に有効です。ホスト直下のパスへ意図的に解決する設計であれば、先頭スラッシュは正しい表現です。目的のAPIパス契約を確認せずに、すべての先頭スラッシュを除去してはいけません。

また、このラボはURIの構文的な解決だけを扱います。URIの利用者入力、許可ホスト検証、パストラバーサル、クエリのエンコード、HTTPリダイレクトなどは別の責務です。

## References

[1] [Oracle: `URI` — normalization, resolution, and hierarchical paths](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URI.html)
