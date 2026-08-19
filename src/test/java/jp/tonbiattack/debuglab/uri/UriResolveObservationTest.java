package jp.tonbiattack.debuglab.uri;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;

import org.junit.jupiter.api.Test;

class UriResolveObservationTest {

    @Test
    void leadingSlashReplacesTheBasePathWhileRelativeReferenceExtendsIt() {
        URI base = URI.create("https://api.example.test/v1/");

        URI relative = base.resolve("invoices/42");
        URI leadingSlash = base.resolve("/invoices/42");

        assertAll(
                () -> assertEquals(URI.create("https://api.example.test/v1/invoices/42"), relative,
                        "相対参照は基底URIの/v1/の下へ解決される"),
                () -> assertEquals(URI.create("https://api.example.test/invoices/42"), leadingSlash,
                        "先頭スラッシュ付き参照は基底パスを置き換えてホスト直下へ解決される")
        );
    }
}
