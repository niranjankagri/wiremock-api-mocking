package com.niranjankagri.checkout;

/**
 * A shipping price.
 *
 * @param priceCents the price in cents
 * @param estimated  true when this is the flat rate, not the carrier's own price
 */
public record ShippingQuote(long priceCents, boolean estimated) {

    static ShippingQuote flatRate() {
        return new ShippingQuote(ShippingClient.FLAT_RATE_CENTS, true);
    }
}
