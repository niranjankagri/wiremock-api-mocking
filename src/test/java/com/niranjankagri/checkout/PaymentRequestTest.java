package com.niranjankagri.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Request matching: what the client must send for the provider to accept it. */
@DisplayName("Payment requests")
class PaymentRequestTest extends MockedApisTest {

    @Test
    void tc101_aChargeCarriesTheApiKeyAndTheOrderAsIdempotencyKey() {
        provider.capturesPayments();

        // The stub only matches with the Authorization and Content-Type headers; without them this test fails
        payments.charge("ORD-7", 1_000, "EUR", "tok_visa");

        assertThat(provider.idempotencyKeys()).containsExactly("ORD-7");
    }

    @Test
    void tc102_theBodyIsExactlyTheProvidersJsonFormat() {
        provider.capturesPayments();

        payments.charge("ORD-7", 1_000, "EUR", "tok_visa");

        provider.verifyChargedExactly("""
                {"orderId":"ORD-7","amountCents":1000,"currency":"EUR","cardToken":"tok_visa"}""");
    }

    @Test
    void tc103_theCardTokenStaysOutOfTheUrl() {
        provider.capturesPayments();

        payments.charge("ORD-7", 1_000, "EUR", "tok_secret");

        // URLs end up in access logs; a request body does not
        assertThat(provider.chargeUrls()).containsExactly("/v1/payments");
    }
}
