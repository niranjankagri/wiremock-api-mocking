package com.niranjankagri.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Stateful scenarios: the same request gets a different answer on each attempt. */
@DisplayName("Payment retries")
class PaymentRetryTest extends MockedApisTest {

    @Test
    void tc121_aShortOutageIsRetriedUntilThePaymentGoesThrough() {
        provider.unavailableThenCaptures(2);

        PaymentOutcome outcome = payments.charge("ORD-7", 1_000, "EUR", "tok_visa");

        assertThat(outcome.state()).isEqualTo(PaymentOutcome.State.CAPTURED);
        assertThat(provider.chargeCount()).isEqualTo(3);
    }

    @Test
    void tc122_everyAttemptCarriesTheSameIdempotencyKey() {
        provider.unavailableThenCaptures(2);

        payments.charge("ORD-7", 1_000, "EUR", "tok_visa");

        // A new key per attempt would let a lost response turn into a double charge
        assertThat(provider.idempotencyKeys()).containsExactly("ORD-7", "ORD-7", "ORD-7");
    }

    @Test
    void tc123_aLongOutageEndsUnknownAfterThreeAttempts() {
        provider.answers(503);

        PaymentOutcome outcome = payments.charge("ORD-7", 1_000, "EUR", "tok_visa");

        assertThat(outcome.state()).isEqualTo(PaymentOutcome.State.UNKNOWN);
        assertThat(outcome.problem()).isEqualTo("HTTP 503");
        assertThat(provider.chargeCount()).isEqualTo(3);
    }

    @Test
    void tc124_theWaitDoublesBetweenAttempts() {
        provider.answers(503);

        payments.charge("ORD-7", 1_000, "EUR", "tok_visa");

        // Two waits for three attempts: nothing is waited for after the last one
        assertThat(sleeper.waits()).containsExactly(Duration.ofMillis(200), Duration.ofMillis(400));
    }

    @Test
    void tc125_aRateLimitIsRespected() {
        provider.rateLimitsOnceThenCaptures("7");

        PaymentOutcome outcome = payments.charge("ORD-7", 1_000, "EUR", "tok_visa");

        assertThat(outcome.state()).isEqualTo(PaymentOutcome.State.CAPTURED);
        assertThat(sleeper.waits()).containsExactly(Duration.ofSeconds(7));
    }

    @Test
    void tc126_aProviderCannotStallTheCheckoutWithAHugeRetryAfter() {
        provider.rateLimitsOnceThenCaptures("86400");

        payments.charge("ORD-7", 1_000, "EUR", "tok_visa");

        assertThat(sleeper.waits()).containsExactly(Duration.ofSeconds(30));
    }

    @Test
    void tc127_aRetryAfterDateFallsBackToTheNormalWait() {
        provider.rateLimitsOnceThenCaptures("Wed, 21 Oct 2026 07:28:00 GMT");

        payments.charge("ORD-7", 1_000, "EUR", "tok_visa");

        assertThat(sleeper.waits()).containsExactly(Duration.ofMillis(200));
    }
}
