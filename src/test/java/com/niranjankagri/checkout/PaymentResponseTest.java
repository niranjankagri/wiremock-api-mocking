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
        // Given: the stub builds the payment id from the request (response templating)
        provider.capturesPayments();

        // When
        PaymentOutcome outcome = payments.charge(CHARGE);

        // Then: pay_ORD-7 can only come from this request, so a hard-coded answer could not pass
        assertThat(outcome.state()).isEqualTo(PaymentOutcome.State.CAPTURED);
        assertThat(outcome.paymentId()).isEqualTo("pay_ORD-7");
    }

    @Test
    void tc112_aDeclineKeepsTheProvidersReasonAndIsNotRetried() {
        // Given
        provider.declines("insufficient_funds");

        // When
        PaymentOutcome outcome = payments.charge(CHARGE);

        // Then: a refusal is a final answer, so one request is enough
        assertThat(outcome.state()).isEqualTo(PaymentOutcome.State.DECLINED);
        assertThat(outcome.declineCode()).isEqualTo("insufficient_funds");
        assertThat(provider.chargeCount()).isEqualTo(1);
    }

    @Test
    void tc113_aWrongApiKeyStopsAtOnce() {
        // Given
        provider.refusesTheApiKey();

        // When / Then: a loud failure, because this is a configuration fault and not a customer's problem
        assertThatThrownBy(() -> payments.charge(CHARGE))
                .isInstanceOf(PaymentRejectedException.class)
                .hasMessageContaining("HTTP 401");
        assertThat(provider.chargeCount()).isEqualTo(1);
    }

    @ParameterizedTest(name = "HTTP {0}")
    @ValueSource(ints = {400, 403, 404, 409, 422})
    void tc114_otherClientErrorsAreNotRetried(int status) {
        // Given
        provider.answers(status);

        // When / Then: the same request would only get the same refusal
        assertThatThrownBy(() -> payments.charge(CHARGE)).isInstanceOf(PaymentRejectedException.class);
        assertThat(provider.chargeCount()).isEqualTo(1);
    }

    @ParameterizedTest(name = "body: {0}")
    @ValueSource(strings = {"not json", "{}", "{\"id\":\"pay_1\"}", "{\"id\":\"pay_1\",\"status\":\"pending\"}"})
    void tc115_aSuccessCodeWithAnUnusableBodyIsNeitherPaidNorFailed(String body) {
        // Given: a 201 that does not say the payment was captured
        provider.answersCreatedWith(body);

        // When
        PaymentOutcome outcome = payments.charge(CHARGE);

        // Then
        assertThat(outcome.state()).isEqualTo(PaymentOutcome.State.UNKNOWN);
    }

    @Test
    void tc116_aDeclineWithAnEmptyReasonStillCountsAsDeclined() {
        // Given
        provider.declines("");

        // When
        PaymentOutcome outcome = payments.charge(CHARGE);

        // Then
        assertThat(outcome.state()).isEqualTo(PaymentOutcome.State.DECLINED);
        assertThat(outcome.declineCode()).isEqualTo("unknown");
    }
}
