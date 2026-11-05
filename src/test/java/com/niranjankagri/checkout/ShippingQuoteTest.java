package com.niranjankagri.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The carrier's price, and the flat rate whenever the carrier gives no usable answer. */
@DisplayName("Shipping quotes")
class ShippingQuoteTest extends MockedApisTest {

    private static final ShippingQuote FLAT_RATE = new ShippingQuote(ShippingClient.FLAT_RATE_CENTS, true);

    @Test
    void tc141_theCarriersPriceIsUsedForThatPostcodeAndWeight() {
        // Given: a stub that only matches these two query parameters
        carrier.quotesFor("10115", 1_200, 650);

        // When / Then
        assertThat(shipping.quote("10115", 1_200)).isEqualTo(new ShippingQuote(650, false));
    }

    @Test
    void tc142_aPostcodeWithASpaceIsSentCorrectly() {
        // Given
        carrier.quotesFor("1011 AB", 1_200, 700);

        // When / Then
        assertThat(shipping.quote("1011 AB", 1_200)).isEqualTo(new ShippingQuote(700, false));
        // WireMock decodes "+" and "%20" alike, so the stub alone cannot tell them apart: check the raw URL
        assertThat(carrier.requestUrls()).containsExactly("/v1/rates?postcode=1011%20AB&weightGrams=1200");
    }

    @ParameterizedTest(name = "HTTP {0}")
    @ValueSource(ints = {204, 400, 404, 429, 500, 503})
    void tc143_anErrorAnswerGivesTheFlatRateWithoutARetry(int status) {
        // Given
        carrier.answers(status);

        // When / Then: one request only, because the customer is waiting at the checkout
        assertThat(shipping.quote("10115", 1_200)).isEqualTo(FLAT_RATE);
        assertThat(carrier.requestCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(Fault.class)
    void tc144_aBrokenConnectionGivesTheFlatRate(Fault fault) {
        // Given
        carrier.breaksConnection(fault);

        // When / Then
        assertThat(shipping.quote("10115", 1_200)).isEqualTo(FLAT_RATE);
    }

    @ParameterizedTest(name = "body: {0}")
    @ValueSource(strings = {"not json", "{}", "{\"priceCents\":\"free\"}", "{\"priceCents\":-500}"})
    void tc145_anUnusableBodyGivesTheFlatRate(String body) {
        // Given: a 200 without a usable price
        carrier.answersWith(body);

        // When / Then
        assertThat(shipping.quote("10115", 1_200)).isEqualTo(FLAT_RATE);
    }

    @Test
    void tc146_aCarrierSlowerThanTheTimeoutGivesTheFlatRate() {
        // Given: 2 s per answer, against a 1 s timeout
        carrier.quotesAfter(2_000);

        // When / Then
        assertThat(shipping.quote("10115", 1_200)).isEqualTo(FLAT_RATE);
    }
}
