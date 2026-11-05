package com.niranjankagri.checkout;

/**
 * One charge, exactly as the payment provider expects it: Jackson writes this record as the request's JSON body, so
 * the field names here are the provider's field names.
 *
 * @param orderId     the order, also used as the idempotency key
 * @param amountCents the total in cents
 * @param currency    the three-letter currency code
 * @param cardToken   the provider's token for the card
 */
public record Charge(String orderId, long amountCents, String currency, String cardToken) {
}
