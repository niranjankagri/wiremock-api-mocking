package com.niranjankagri.checkout;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.niranjankagri.checkout.support.CheckoutRequests.anOrder;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.niranjankagri.checkout.CheckoutResult.Status;
import com.niranjankagri.checkout.support.RecordingSleeper;
import com.niranjankagri.checkout.support.TestClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Stubs as JSON files in src/test/resources/wiremock, not Java: a tester can add one without touching the code. */
@DisplayName("JSON mapping files")
class MappingFilesTest {

    @RegisterExtension
    static final WireMockExtension API = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().usingFilesUnderClasspath("wiremock"))
            .failOnUnmatchedRequests(true)
            .build();

    private CheckoutService checkout;

    @BeforeEach
    void connectClients() {
        checkout = new CheckoutService(TestClients.payments(API.baseUrl(), new RecordingSleeper()),
                TestClients.shipping(API.baseUrl()));
    }

    @Test
    void tc161_theFilesServeANormalCheckout() {
        CheckoutResult result = checkout.checkout(anOrder().withOrderId("ORD-42").build());

        assertThat(result.status()).isEqualTo(Status.PAID);
        assertThat(result.paymentId()).isEqualTo("pay_ORD-42");
        // The price comes from __files/shipping-rate.json, the response body kept in its own file
        assertThat(result.shippingCents()).isEqualTo(495);
    }

    @Test
    void tc162_theMoreSpecificMappingWinsByPriority() {
        CheckoutResult result = checkout.checkout(anOrder().withCardToken("tok_declined").build());

        assertThat(result.status()).isEqualTo(Status.DECLINED);
        assertThat(result.declineCode()).isEqualTo("do_not_honor");
    }
}
