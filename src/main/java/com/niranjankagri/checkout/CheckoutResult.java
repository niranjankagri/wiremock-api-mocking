package com.niranjankagri.checkout;

/**
 * What happened at the checkout.
 *
 * @param status            the payment's state
 * @param paymentId         the provider's payment id; null unless paid
 * @param declineCode       the provider's reason; null unless declined
 * @param chargedCents      the total sent to the provider: goods plus shipping
 * @param shippingCents     the shipping part of that total
 * @param shippingEstimated true when the carrier did not answer and the flat rate was used
 */
public record CheckoutResult(Status status, String paymentId, String declineCode, long chargedCents,
                             long shippingCents, boolean shippingEstimated) {

    /** The three states a payment can end in. */
    public enum Status {
        /** The provider took the money. */
        PAID,
        /** The provider refused the card: no money moved. */
        DECLINED,
        /** No clear answer: the money may or may not have moved, so nobody may treat this as failed. */
        PAYMENT_UNKNOWN
    }
}
