package jp.tonbiattack.debuglab.uri;

import java.net.URI;

/**
 * APIバージョンを含む基底URIから、パートナーAPIの相対参照を解決します。
 */
public class PartnerApiUriRouter {

    private final URI baseUri = URI.create("https://api.example.test/v1/");
    private URI lastAcceptedUri;
    private int acceptedRouteCount;

    public RouteOutcome routeHealth() {
        return acceptIfInsideApiVersion(baseUri.resolve("health"));
    }

    public RouteOutcome routeInvoice(String invoiceId) {
        return acceptIfInsideApiVersion(baseUri.resolve("invoices/" + invoiceId));
    }

    public URI lastAcceptedUri() {
        return lastAcceptedUri;
    }

    public int acceptedRouteCount() {
        return acceptedRouteCount;
    }

    private RouteOutcome acceptIfInsideApiVersion(URI candidate) {
        if (!candidate.getPath().startsWith(baseUri.getPath())) {
            return RouteOutcome.OUTSIDE_API_VERSION;
        }
        lastAcceptedUri = candidate;
        acceptedRouteCount++;
        return RouteOutcome.ROUTED_TO_API_VERSION;
    }
}
