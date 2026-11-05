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
        carrier.quotes(650);
        provider.capturesPayments();

        CheckoutResult result = checkout.checkout(anOrder().withAmountCents(4_999).build());

        assertThat(result.status()).isEqualTo(Status.PAID);
        assertThat(result.chargedCents()).isEqualTo(5_649);
        provider.verifyChargedExactly("""
                {"orderId":"ORD-1001","amountCents":5649,"currency":"EUR","cardToken":"tok_visa"}""");
    }

    @Test
    void tc152_aCarrierOutageDoesNotLoseTheSale() {
        carrier.answers(503);
        provider.capturesPayments();

        CheckoutResult result = checkout.checkout(anOrder().withAmountCents(4_999).build());

        assertThat(result.status()).isEqualTo(Status.PAID);
        assertThat(result.shippingEstimated()).isTrue();
        assertThat(result.chargedCents()).isEqualTo(4_999 + ShippingClient.FLAT_RATE_CENTS);
    }

    @Test
    void tc153_aDeclinedCardGivesNoPaymentId() {
        carrier.quotes(650);
        provider.declines("card_expired");

        CheckoutResult result = checkout.checkout(anOrder().build());

        assertThat(result.status()).isEqualTo(Status.DECLINED);
        assertThat(result.declineCode()).isEqualTo("card_expired");
        assertThat(result.paymentId()).isNull();
    }

    @Test
    void tc154_aPaymentOutageIsReportedAsUnknownNotAsDeclined() {
        carrier.quotes(650);
        provider.answers(503);

        CheckoutResult result = checkout.checkout(anOrder().build());

        assertThat(result.status()).isEqualTo(Status.PAYMENT_UNKNOWN);
    }

    @Test
    void tc155_anInvalidOrderReachesNeitherApi() {
        assertThatThrownBy(() -> checkout.checkout(anOrder().withAmountCents(0).build()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(carrier.requestCount()).isZero();
        assertThat(provider.chargeCount()).isZero();
    }

    @Test
    void tc156_repeatingACheckoutReturnsTheSamePayment() {
        carrier.quotes(650);
        provider.remembersIdempotencyKeys();

        CheckoutResult first = checkout.checkout(anOrder().build());
        CheckoutResult repeat = checkout.checkout(anOrder().build());

        assertThat(repeat.paymentId()).isEqualTo(first.paymentId());
        assertThat(provider.idempotencyKeys()).containsExactly("ORD-1001", "ORD-1001");
    }
}
