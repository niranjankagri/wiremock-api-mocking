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

    // @EnumSource runs the test once per WireMock fault, including any fault a later WireMock version adds
    @ParameterizedTest
    @EnumSource(Fault.class)
    void tc131_aBrokenConnectionEndsUnknownNeverFailed(Fault fault) {
        // Given: every attempt breaks
        provider.breaksConnection(fault);

        // When
        PaymentOutcome outcome = payments.charge(CHARGE);

        // Then: the provider may have taken the money before the connection broke
        assertThat(outcome.state()).isEqualTo(PaymentOutcome.State.UNKNOWN);
        assertThat(provider.chargeCount()).isEqualTo(3);
    }

    @ParameterizedTest
    @EnumSource(Fault.class)
    void tc132_oneBrokenConnectionIsRecoveredByARetry(Fault fault) {
        // Given: the first attempt breaks, the second works
        provider.breaksConnectionOnceThenCaptures(fault);

        // When
        PaymentOutcome outcome = payments.charge(CHARGE);

        // Then: the same key both times, so the provider can recognise the second attempt as a repeat
        assertThat(outcome.state()).isEqualTo(PaymentOutcome.State.CAPTURED);
        assertThat(provider.idempotencyKeys()).containsExactly("ORD-7", "ORD-7");
    }

    @Test
    void tc133_aProviderSlowerThanTheTimeoutEndsUnknown() {
        // Given: 2 s per answer, against a 1 s timeout
        provider.capturesAfter(2_000);

        // When
        PaymentOutcome outcome = payments.charge(CHARGE);

        // Then: counted on the client side, because WireMock logs a delayed request only after its own delay
        assertThat(outcome.state()).isEqualTo(PaymentOutcome.State.UNKNOWN);
        assertThat(outcome.problem()).isEqualTo("HttpTimeoutException");
        assertThat(sleeper.waits()).hasSize(2);
    }

    @Test
    void tc134_aSlowProviderWithinTheTimeoutIsFine() {
        // Given: slow, but within the timeout
        provider.capturesAfter(300);

        // When
        PaymentOutcome outcome = payments.charge(CHARGE);

        // Then: no retry was needed
        assertThat(outcome.state()).isEqualTo(PaymentOutcome.State.CAPTURED);
        assertThat(sleeper.waits()).isEmpty();
    }

    @Test
    void tc135_anInterruptedChargeEndsUnknownAndStaysInterrupted() {
        // Given: the thread is interrupted before the call
        provider.capturesPayments();
        Thread.currentThread().interrupt();

        // When
        PaymentOutcome outcome = payments.charge(CHARGE);
        // Reading the flag also clears it, so the next test starts clean
        boolean stillInterrupted = Thread.interrupted();

        // Then: the caller can still see the interrupt, and the payment isn't reported as failed
        assertThat(stillInterrupted).isTrue();
        assertThat(outcome.state()).isEqualTo(PaymentOutcome.State.UNKNOWN);
        assertThat(outcome.problem()).isEqualTo("interrupted");
    }
}
