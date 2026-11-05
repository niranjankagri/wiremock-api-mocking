package com.niranjankagri.checkout;

/**
 * The provider's answer to a charge.
 *
 * @param state       captured, declined or unknown
 * @param paymentId   the provider's id; null unless captured
 * @param declineCode the provider's reason; null unless declined
 * @param problem     what went wrong; null unless unknown
 */
public record PaymentOutcome(State state, String paymentId, String declineCode, String problem) {

    /** The three possible answers. */
    public enum State { CAPTURED, DECLINED, UNKNOWN }

    static PaymentOutcome captured(String paymentId) {
        return new PaymentOutcome(State.CAPTURED, paymentId, null, null);
    }

    static PaymentOutcome declined(String declineCode) {
        return new PaymentOutcome(State.DECLINED, null, declineCode, null);
    }

    static PaymentOutcome unknown(String problem) {
        return new PaymentOutcome(State.UNKNOWN, null, null, problem);
    }
}
