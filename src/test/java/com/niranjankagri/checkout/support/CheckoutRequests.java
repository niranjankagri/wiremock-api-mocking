package com.niranjankagri.checkout.support;

import com.niranjankagri.checkout.CheckoutRequest;

/** Test data: a valid order by default, so a test only states the one value it is about. */
public final class CheckoutRequests {

    private String orderId = "ORD-1001";
    private long amountCents = 4_999;
    private String cardToken = "tok_visa";

    private CheckoutRequests() {
    }

    /** @return a builder holding a valid order */
    public static CheckoutRequests anOrder() {
        return new CheckoutRequests();
    }

    /**
     * @param value the order id
     * @return this builder
     */
    public CheckoutRequests withOrderId(String value) {
        orderId = value;
        return this;
    }

    /**
     * @param value the goods total
     * @return this builder
     */
    public CheckoutRequests withAmountCents(long value) {
        amountCents = value;
        return this;
    }

    /**
     * @param value the card token
     * @return this builder
     */
    public CheckoutRequests withCardToken(String value) {
        cardToken = value;
        return this;
    }

    /** @return the request */
    public CheckoutRequest build() {
        return new CheckoutRequest(orderId, amountCents, "EUR", cardToken, "10115", 1_200);
    }
}
