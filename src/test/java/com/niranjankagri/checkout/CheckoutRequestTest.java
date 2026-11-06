package com.niranjankagri.checkout;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The rules that stop a bad order before any API is called. No WireMock needed: no HTTP is involved. */
@DisplayName("Checkout request rules")
class CheckoutRequestTest {

    @Test
    void tc181_aValidRequestIsAccepted() {
        assertThatNoException().isThrownBy(() -> new CheckoutRequest("ORD-1", 1, "EUR", "tok_visa", "10115", 1));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("invalidRequests")
    void tc182_anInvalidValueIsRefused(String field, Object value, Runnable create) {
        // The message names the field, so a caller can tell which value was wrong
        assertThatIllegalArgumentException().isThrownBy(create::run).withMessageContaining(field);
    }

    private static Stream<Arguments> invalidRequests() {
        return Stream.of(
                invalid("orderId", null, () -> new CheckoutRequest(null, 1, "EUR", "tok", "10115", 1)),
                invalid("orderId", "blank", () -> new CheckoutRequest(" ", 1, "EUR", "tok", "10115", 1)),
                invalid("cardToken", null, () -> new CheckoutRequest("ORD-1", 1, "EUR", null, "10115", 1)),
                invalid("postcode", "blank", () -> new CheckoutRequest("ORD-1", 1, "EUR", "tok", "", 1)),
                invalid("amountCents", 0, () -> new CheckoutRequest("ORD-1", 0, "EUR", "tok", "10115", 1)),
                invalid("amountCents", -1, () -> new CheckoutRequest("ORD-1", -1, "EUR", "tok", "10115", 1)),
                invalid("currency", null, () -> new CheckoutRequest("ORD-1", 1, null, "tok", "10115", 1)),
                invalid("currency", "eur", () -> new CheckoutRequest("ORD-1", 1, "eur", "tok", "10115", 1)),
                invalid("currency", "EURO", () -> new CheckoutRequest("ORD-1", 1, "EURO", "tok", "10115", 1)),
                invalid("weightGrams", 0, () -> new CheckoutRequest("ORD-1", 1, "EUR", "tok", "10115", 0)),
                invalid("weightGrams", -5, () -> new CheckoutRequest("ORD-1", 1, "EUR", "tok", "10115", -5)));
    }

    private static Arguments invalid(String field, Object value, Runnable create) {
        return Arguments.of(field, value, create);
    }
}
