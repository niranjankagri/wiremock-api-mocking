package com.niranjankagri.checkout;

import static com.niranjankagri.checkout.support.CheckoutRequests.anOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.niranjankagri.checkout.CheckoutResult.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Both APIs together, the way the shop uses them. */
@DisplayName("Checkout flow")
class CheckoutFlowTest extends MockedApisTest {

    @Test
    void tc151_goodsAndShippingAreChargedAsOneTotal() {
        // Given
        carrier.quotes(650);
        provider.capturesPayments();

        // When
        CheckoutResult result = checkout.checkout(anOrder().withAmountCents(4_999).build());

        // Then: the result and the request the provider really received both show 49.99 + 6.50
        assertThat(result.status()).isEqualTo(Status.PAID);
        assertThat(result.chargedCents()).isEqualTo(5_649);
        provider.verifyChargedExactly("""
                {"orderId":"ORD-1001","amountCents":5649,"currency":"EUR","cardToken":"tok_visa"}""");
    }

    @Test
    void tc152_aCarrierOutageDoesNotLoseTheSale() {
        // Given: only the carrier is down
        carrier.answers(503);
        provider.capturesPayments();

        // When
        CheckoutResult result = checkout.checkout(anOrder().withAmountCents(4_999).build());

        // Then: paid, with the flat rate, and marked so the shop can tell the customer
        assertThat(result.status()).isEqualTo(Status.PAID);
        assertThat(result.shippingEstimated()).isTrue();
        assertThat(result.chargedCents()).isEqualTo(4_999 + ShippingClient.FLAT_RATE_CENTS);
    }

    @Test
    void tc153_aDeclinedCardGivesNoPaymentId() {
        // Given
        carrier.quotes(650);
        provider.declines("card_expired");

        // When
        CheckoutResult result = checkout.checkout(anOrder().build());

        // Then
        assertThat(result.status()).isEqualTo(Status.DECLINED);
        assertThat(result.declineCode()).isEqualTo("card_expired");
        assertThat(result.paymentId()).isNull();
    }

    @Test
    void tc154_aPaymentOutageIsReportedAsUnknownNotAsDeclined() {
        // Given
        carrier.quotes(650);
        provider.answers(503);

        // When
        CheckoutResult result = checkout.checkout(anOrder().build());

        // Then: "declined" would invite the customer to pay again, perhaps for the second time
        assertThat(result.status()).isEqualTo(Status.PAYMENT_UNKNOWN);
    }

    @Test
    void tc155_anInvalidOrderReachesNeitherApi() {
        // When / Then: refused while the request is built, before any HTTP call
        assertThatThrownBy(() -> checkout.checkout(anOrder().withAmountCents(0).build()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(carrier.requestCount()).isZero();
        assertThat(provider.chargeCount()).isZero();
    }

    @Test
    void tc156_repeatingACheckoutReturnsTheSamePayment() {
        // Given: a provider that answers a repeated key with the existing payment
        carrier.quotes(650);
        provider.remembersIdempotencyKeys();

        // When: the shop runs the same order twice, for example after a double click
        CheckoutResult first = checkout.checkout(anOrder().build());
        CheckoutResult repeat = checkout.checkout(anOrder().build());

        // Then
        assertThat(repeat.paymentId()).isEqualTo(first.paymentId());
        assertThat(provider.idempotencyKeys()).containsExactly("ORD-1001", "ORD-1001");
    }
}
