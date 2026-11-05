package com.niranjankagri.checkout;

/** The checkout: asks the carrier for the shipping price, then charges goods plus shipping. */
public final class CheckoutService {

    private final PaymentClient payments;
    private final ShippingClient shipping;

    /**
     * @param payments the payment provider's client
     * @param shipping the shipping carrier's client
     */
    public CheckoutService(PaymentClient payments, ShippingClient shipping) {
        this.payments = payments;
        this.shipping = shipping;
    }

    /**
     * Runs one checkout.
     *
     * @param request the validated order
     * @return the outcome; a decline or an unclear payment is a result, not an exception
     * @throws PaymentRejectedException if the provider refuses the request itself, for example a wrong API key
     */
    public CheckoutResult checkout(CheckoutRequest request) {
        ShippingQuote quote = shipping.quote(request.postcode(), request.weightGrams());
        long total = request.amountCents() + quote.priceCents();
        PaymentOutcome payment = payments.charge(request.orderId(), total, request.currency(), request.cardToken());
        return new CheckoutResult(toStatus(payment), payment.paymentId(), payment.declineCode(), total,
                quote.priceCents(), quote.estimated());
    }

    private static CheckoutResult.Status toStatus(PaymentOutcome payment) {
        return switch (payment.state()) {
            case CAPTURED -> CheckoutResult.Status.PAID;
            case DECLINED -> CheckoutResult.Status.DECLINED;
            case UNKNOWN -> CheckoutResult.Status.PAYMENT_UNKNOWN;
        };
    }
}
