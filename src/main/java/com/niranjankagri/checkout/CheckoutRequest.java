package com.niranjankagri.checkout;

/**
 * What the shop asks the checkout to do. Invalid values are refused here, before any API is called.
 *
 * @param orderId     the shop's order number, also used as the payment's idempotency key
 * @param amountCents the goods total in cents, without shipping
 * @param currency    the three-letter currency code
 * @param cardToken   the provider's token for the card (never a card number)
 * @param postcode    the delivery postcode
 * @param weightGrams the parcel weight
 */
public record CheckoutRequest(String orderId, long amountCents, String currency, String cardToken, String postcode,
                              int weightGrams) {

    /** Refuses a request that could never be charged or shipped. */
    public CheckoutRequest {
        requireText(orderId, "orderId");
        requireText(cardToken, "cardToken");
        requireText(postcode, "postcode");
        if (amountCents <= 0) {
            throw new IllegalArgumentException("amountCents must be positive");
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be a three-letter code");
        }
        if (weightGrams <= 0) {
            throw new IllegalArgumentException("weightGrams must be positive");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
