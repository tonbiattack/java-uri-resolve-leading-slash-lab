# `URI.resolve`の先頭スラッシュでAPIバージョンのパスを失う

Java標準ライブラリの`java.net.URI`を題材に、**エンドポイント参照の先頭スラッシュにより、基底URIの`/v1/`パスを失う**問題を、失敗するテスト、原因の直接観測、最小修正、回帰テストの順に追うデバッグ教材です。既定ブランチの`main`は成功状態に保ち、意図的に失敗する状態はGit履歴に独立して残します。

## この題材で守る契約

> 基底URI`https://api.example.test/v1/`で、`health`を正常にルーティングした後、請求書ID`42`をルーティングする場合、`https://api.example.test/v1/invoices/42`を採用し、採用件数を二件にする。

| 段階 | 実施内容 | 確認すること |
| --- | --- | --- |
| 再現 | `"/invoices/42"`を基底URIへ解決する | `OUTSIDE_API_VERSION`となり、最後の採用URIは`/v1/health`のまま、採用件数も1のままとなる |
| 観測 | `"invoices/42"`と`"/invoices/42"`を同じ基底URIへ解決する | 前者は`/v1/`を維持し、後者は`/v1/`を置き換えてホスト直下へ解決される |
| 修正 | 先頭スラッシュを除いた相対参照を使う | 請求書URIが基底パスの下へ解決される |
| 回帰防止 | 同じルーターテストを再実行する | 戻り値、最後に採用したURI、採用件数がすべて更新される |

## 必要な環境

| 項目 | バージョン |
| --- | --- |
| JDK | 21 |
| Maven | 3.8以上 |
| テストランナー | JUnit Jupiter 5.11.4 |
| アプリケーションフレームワーク | 不使用 |

## 最短の開始手順

```bash
mvn --batch-mode clean test
```

検証済みの`main`では、3テストがすべて成功します。

## バグを再現する

```bash
git checkout 254a582
mvn --batch-mode test -Dtest=PartnerApiUriRouterTest
# expected: <ROUTED_TO_API_VERSION> but was: <OUTSIDE_API_VERSION>
# expected: <https://api.example.test/v1/invoices/42>
# but was:  <https://api.example.test/v1/health>
# expected: <2> but was: <1>

git checkout main
mvn --batch-mode clean test
# Tests run: 3, Failures: 0, Errors: 0
```

バグコミットでは設定やネットワークではなく、APIバージョンのパス内に解決する契約だけが失敗します。完全な出力は[`evidence/01-bug-service-test-output.txt`](evidence/01-bug-service-test-output.txt)に保存しています。

## 原因の要点

`URI.resolve`は、階層URIの参照を基底URIに対して解決します。参照のパスが先頭スラッシュで始まる場合、そのパスは絶対パスです。[1] したがって基底URIが`https://api.example.test/v1/`であっても、`"/invoices/42"`は`https://api.example.test/invoices/42`になります。

基底URIの`/v1/`を維持するには、`"invoices/42"`のように先頭スラッシュを持たない相対参照を渡します。[1] この差は文字列連結の見た目では小さい一方、実際のルーティング先を変更します。

## プロジェクト構成

```text
.
├── docs/
│   ├── debugging-record.md      # 観測・仮説・原因・修正・回帰保証
│   ├── novelty-report.md        # 既存Java記事との四軸比較
│   └── topic-brief.md           # 実装前に固定した契約と再現境界
├── evidence/
│   ├── 01-bug-service-test-output.txt
│   ├── 02-uri-resolve-observation-output.txt
│   └── 03-fixed-full-test-output.txt
├── src/main/java/.../uri/
│   ├── PartnerApiUriRouter.java
│   └── RouteOutcome.java
└── src/test/java/.../uri/
    ├── PartnerApiUriRouterTest.java
    └── UriResolveObservationTest.java
```

詳細な調査手順は[デバッグ記録](docs/debugging-record.md)、既存コンテンツとの差分は[題材重複調査レポート](docs/novelty-report.md)を参照してください。

## スコープ

この教材は固定の基底URI・固定の請求書ID・パス解決だけを対象にします。HTTP通信、認証、クエリパラメータ、パーセントエンコード、URIの利用者入力、リダイレクト、DNS、プロキシは対象外です。

先頭スラッシュを取り除く修正は、参照が**基底URIのパス配下**にあることが契約の場合にだけ適用します。ホスト直下の絶対パスへ意図的に解決する設計であれば、先頭スラッシュは正しい表現です。

## References

[1] [Oracle: `URI` — normalization, resolution, and hierarchical paths](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URI.html)
