package jp.tonbiattack.debuglab.uri;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;

import org.junit.jupiter.api.Test;

class PartnerApiUriRouterTest {

    @Test
    void invoiceRoute_keepsTheApiVersionPathAndUpdatesAcceptedState() {
        PartnerApiUriRouter router = new PartnerApiUriRouter();
        router.routeHealth();

        RouteOutcome outcome = router.routeInvoice("42");

        assertAll(
                () -> assertEquals(RouteOutcome.ROUTED_TO_API_VERSION, outcome,
                        "請求書URIをAPIバージョン配下としてルーティングする"),
                () -> assertEquals(URI.create("https://api.example.test/v1/invoices/42"),
                        router.lastAcceptedUri(),
                        "最後に採用したURIは/v1/の下に請求書IDを持つ"),
                () -> assertEquals(2, router.acceptedRouteCount(),
                        "healthと請求書URIの二件を採用する")
        );
    }

    @Test
    void healthRoute_withRelativeReferenceRemainsInsideApiVersion() {
        PartnerApiUriRouter router = new PartnerApiUriRouter();

        RouteOutcome outcome = router.routeHealth();

        assertAll(
                () -> assertEquals(RouteOutcome.ROUTED_TO_API_VERSION, outcome),
                () -> assertEquals(URI.create("https://api.example.test/v1/health"), router.lastAcceptedUri()),
                () -> assertEquals(1, router.acceptedRouteCount())
        );
    }
}
