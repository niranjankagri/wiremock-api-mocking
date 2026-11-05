package com.niranjankagri.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Fault injection: network failures no real sandbox produces on demand. */
@DisplayName("Payment network faults")
class PaymentFaultTest extends MockedApisTest {

    @ParameterizedTest
    @EnumSource(Fault.class)
    void tc131_aBrokenConnectionEndsUnknownNeverFailed(Fault fault) {
        provider.breaksConnection(fault);

        PaymentOutcome outcome = payments.charge("ORD-7", 1_000, "EUR", "tok_visa");

        // The provider may have taken the money before the connection broke
        assertThat(outcome.state()).isEqualTo(PaymentOutcome.State.UNKNOWN);
        assertThat(provider.chargeCount()).isEqualTo(3);
    }

    @ParameterizedTest
    @EnumSource(Fault.class)
    void tc132_oneBrokenConnectionIsRecoveredByARetry(Fault fault) {
        provider.breaksConnectionOnceThenCaptures(fault);

        PaymentOutcome outcome = payments.charge("ORD-7", 1_000, "EUR", "tok_visa");

        assertThat(outcome.state()).isEqualTo(PaymentOutcome.State.CAPTURED);
        assertThat(provider.idempotencyKeys()).containsExactly("ORD-7", "ORD-7");
    }

    @Test
    void tc133_aProviderSlowerThanTheTimeoutEndsUnknown() {
        provider.capturesAfter(2_000);

        PaymentOutcome outcome = payments.charge("ORD-7", 1_000, "EUR", "tok_visa");

        assertThat(outcome.state()).isEqualTo(PaymentOutcome.State.UNKNOWN);
        assertThat(outcome.problem()).isEqualTo("HttpTimeoutException");
        // Counted on the client side: WireMock logs a delayed request only after its own delay has passed
        assertThat(sleeper.waits()).hasSize(2);
    }

    @Test
    void tc134_aSlowProviderWithinTheTimeoutIsFine() {
        provider.capturesAfter(300);

        PaymentOutcome outcome = payments.charge("ORD-7", 1_000, "EUR", "tok_visa");

        assertThat(outcome.state()).isEqualTo(PaymentOutcome.State.CAPTURED);
        assertThat(sleeper.waits()).isEmpty();
    }
}
