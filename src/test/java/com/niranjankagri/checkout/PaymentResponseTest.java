package com.niranjankagri.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Each answer the provider can give, and what the client makes of it. */
@DisplayName("Payment responses")
class PaymentResponseTest extends MockedApisTest {

    @Test
    void tc111_aCapturedPaymentReturnsTheProvidersId() {
        provider.capturesPayments();

        PaymentOutcome outcome = payments.charge("ORD-7", 1_000, "EUR", "tok_visa");

        assertThat(outcome.state()).isEqualTo(PaymentOutcome.State.CAPTURED);
        assertThat(outcome.paymentId()).isEqualTo("pay_ORD-7");
    }

    @Test
    void tc112_aDeclineKeepsTheProvidersReasonAndIsNotRetried() {
        provider.declines("insufficient_funds");

        PaymentOutcome outcome = payments.charge("ORD-7", 1_000, "EUR", "tok_visa");

        assertThat(outcome.state()).isEqualTo(PaymentOutcome.State.DECLINED);
        assertThat(outcome.declineCode()).isEqualTo("insufficient_funds");
        assertThat(provider.chargeCount()).isEqualTo(1);
    }

    @Test
    void tc113_aWrongApiKeyStopsAtOnce() {
        provider.refusesTheApiKey();

        assertThatThrownBy(() -> payments.charge("ORD-7", 1_000, "EUR", "tok_visa"))
                .isInstanceOf(PaymentRejectedException.class)
                .hasMessageContaining("HTTP 401");
        assertThat(provider.chargeCount()).isEqualTo(1);
    }

    @ParameterizedTest(name = "HTTP {0}")
    @ValueSource(ints = {400, 403, 404, 409, 422})
    void tc114_otherClientErrorsAreNotRetried(int status) {
        provider.answers(status);

        assertThatThrownBy(() -> payments.charge("ORD-7", 1_000, "EUR", "tok_visa"))
                .isInstanceOf(PaymentRejectedException.class);
        assertThat(provider.chargeCount()).isEqualTo(1);
    }

    @ParameterizedTest(name = "body: {0}")
    @ValueSource(strings = {"not json", "{}", "{\"id\":\"pay_1\"}", "{\"id\":\"pay_1\",\"status\":\"pending\"}"})
    void tc115_aSuccessCodeWithAnUnusableBodyIsNeitherPaidNorFailed(String body) {
        provider.answersCreatedWith(body);

        PaymentOutcome outcome = payments.charge("ORD-7", 1_000, "EUR", "tok_visa");

        assertThat(outcome.state()).isEqualTo(PaymentOutcome.State.UNKNOWN);
    }
}
