package com.niranjankagri.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Request matching: what the client must send for the provider to accept it. */
@DisplayName("Payment requests")
class PaymentRequestTest extends MockedApisTest {

    @Test
    void tc101_aChargeCarriesTheApiKeyAndTheOrderAsIdempotencyKey() {
        // Given: a stub that only matches with the Authorization, Content-Type and Idempotency-Key headers
        provider.capturesPayments();

        // When: the client charges; a missing header would be an unmatched request, which fails this test
        payments.charge(CHARGE);

        // Then: the key sent is the order number
        assertThat(provider.idempotencyKeys()).containsExactly("ORD-7");
    }

    @Test
    void tc102_theBodyIsExactlyTheProvidersJsonFormat() {
        // Given
        provider.capturesPayments();

        // When
        payments.charge(CHARGE);

        // Then: compared as JSON, so field order and spacing don't matter, but a missing or extra field does
        provider.verifyChargedExactly("""
                {"orderId":"ORD-7","amountCents":1000,"currency":"EUR","cardToken":"tok_visa"}""");
    }

    @Test
    void tc103_theCardTokenStaysOutOfTheUrl() {
        // Given
        provider.capturesPayments();

        // When
        payments.charge(CHARGE);

        // Then: no query string at all. URLs end up in access logs; a request body does not.
        assertThat(provider.chargeUrls()).containsExactly("/v1/payments");
    }
}
