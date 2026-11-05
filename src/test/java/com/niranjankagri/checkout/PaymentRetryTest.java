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
        // Given: 503, 503, then 201
        provider.unavailableThenCaptures(2);

        // When
        PaymentOutcome outcome = payments.charge(CHARGE);

        // Then
        assertThat(outcome.state()).isEqualTo(PaymentOutcome.State.CAPTURED);
        assertThat(provider.chargeCount()).isEqualTo(3);
    }

    @Test
    void tc122_everyAttemptCarriesTheSameIdempotencyKey() {
        // Given
        provider.unavailableThenCaptures(2);

        // When
        payments.charge(CHARGE);

        // Then: a new key per attempt would let a lost response turn into a double charge
        assertThat(provider.idempotencyKeys()).containsExactly("ORD-7", "ORD-7", "ORD-7");
    }

    @Test
    void tc123_aLongOutageEndsUnknownAfterThreeAttempts() {
        // Given: 503 on every request
        provider.answers(503);

        // When
        PaymentOutcome outcome = payments.charge(CHARGE);

        // Then: unknown, not failed, and the reason is kept for the logs
        assertThat(outcome.state()).isEqualTo(PaymentOutcome.State.UNKNOWN);
        assertThat(outcome.problem()).isEqualTo("HTTP 503");
        assertThat(provider.chargeCount()).isEqualTo(3);
    }

    @Test
    void tc124_theWaitDoublesBetweenAttempts() {
        // Given
        provider.answers(503);

        // When
        payments.charge(CHARGE);

        // Then: two waits for three attempts, because nothing is waited for after the last one
        assertThat(sleeper.waits()).containsExactly(Duration.ofMillis(200), Duration.ofMillis(400));
    }

    @Test
    void tc125_aRateLimitIsRespected() {
        // Given: 429 with "Retry-After: 7", then 201
        provider.rateLimitsOnceThenCaptures("7");

        // When
        PaymentOutcome outcome = payments.charge(CHARGE);

        // Then: the provider's wait replaces the client's own 200 ms
        assertThat(outcome.state()).isEqualTo(PaymentOutcome.State.CAPTURED);
        assertThat(sleeper.waits()).containsExactly(Duration.ofSeconds(7));
    }

    @Test
    void tc126_aProviderCannotStallTheCheckoutWithAHugeRetryAfter() {
        // Given: a wait of one day
        provider.rateLimitsOnceThenCaptures("86400");

        // When
        payments.charge(CHARGE);

        // Then: capped at the policy's 30 seconds
        assertThat(sleeper.waits()).containsExactly(Duration.ofSeconds(30));
    }

    @Test
    void tc127_aRetryAfterDateFallsBackToTheNormalWait() {
        // Given: the header's other legal form, a date
        provider.rateLimitsOnceThenCaptures("Wed, 21 Oct 2026 07:28:00 GMT");

        // When
        payments.charge(CHARGE);

        // Then
        assertThat(sleeper.waits()).containsExactly(Duration.ofMillis(200));
    }
}
