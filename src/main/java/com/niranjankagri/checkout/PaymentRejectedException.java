package com.niranjankagri.checkout;

/** The provider refused the request itself (wrong key, invalid data): trying again cannot help. */
public final class PaymentRejectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** @param message what the provider refused */
    public PaymentRejectedException(String message) {
        super(message);
    }
}
