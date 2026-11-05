package com.niranjankagri.checkout;

import static com.niranjankagri.checkout.support.CheckoutRequests.anOrder;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.niranjankagri.checkout.CheckoutResult.Status;
import com.niranjankagri.checkout.support.MockServers;
import com.niranjankagri.checkout.support.RecordingSleeper;
import com.niranjankagri.checkout.support.TestClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Stubs as JSON files in src/test/resources/wiremock, not Java: a tester can add one without touching the code. */
@DisplayName("JSON mapping files")
class MappingFilesTest {

    // One server plays both APIs here: the mapping files tell them apart by path
    @RegisterExtension
    static final WireMockExtension API = MockServers.fromFiles("wiremock");

    private CheckoutService checkout;

    @BeforeEach
    void connectClients() {
        checkout = new CheckoutService(TestClients.payments(API.baseUrl(), new RecordingSleeper()),
                TestClients.shipping(API.baseUrl()));
    }

    @Test
    void tc161_theFilesServeANormalCheckout() {
        // When: no stub is set up in Java
        CheckoutResult result = checkout.checkout(anOrder().withOrderId("ORD-42").build());

        // Then: the id comes from the template in the mapping, the price from __files/shipping-rate.json
        assertThat(result.status()).isEqualTo(Status.PAID);
        assertThat(result.paymentId()).isEqualTo("pay_ORD-42");
        assertThat(result.shippingCents()).isEqualTo(495);
    }

    @Test
    void tc162_theMoreSpecificMappingWinsByPriority() {
        // When: this token matches both payment mappings
        CheckoutResult result = checkout.checkout(anOrder().withCardToken("tok_declined").build());

        // Then: the decline has priority 1, the general capture 5; the lower number wins
        assertThat(result.status()).isEqualTo(Status.DECLINED);
        assertThat(result.declineCode()).isEqualTo("do_not_honor");
    }
}
