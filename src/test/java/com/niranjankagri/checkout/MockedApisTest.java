package com.niranjankagri.checkout;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.niranjankagri.checkout.stubs.PaymentProviderStub;
import com.niranjankagri.checkout.stubs.ShippingCarrierStub;
import com.niranjankagri.checkout.support.MockServers;
import com.niranjankagri.checkout.support.RecordingSleeper;
import com.niranjankagri.checkout.support.TestClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Life cycle of the tests: two WireMock servers on free ports, one per third-party API, and fresh real clients for
 * each test. WireMock resets stubs and its request log before every test.
 */
abstract class MockedApisTest {

    /** A valid charge, for tests that are not about its values. */
    protected static final Charge CHARGE = new Charge("ORD-7", 1_000, "EUR", "tok_visa");

    // Static: one server per test class, because starting one for every test would dominate the run time
    @RegisterExtension
    static final WireMockExtension PAYMENT_API = MockServers.empty();

    @RegisterExtension
    static final WireMockExtension SHIPPING_API = MockServers.empty();

    // Not static: JUnit creates a new test object per test, so each test gets an empty sleeper
    protected final RecordingSleeper sleeper = new RecordingSleeper();
    protected final PaymentProviderStub provider = new PaymentProviderStub(PAYMENT_API);
    protected final ShippingCarrierStub carrier = new ShippingCarrierStub(SHIPPING_API);

    protected PaymentClient payments;
    protected ShippingClient shipping;
    protected CheckoutService checkout;

    @BeforeEach
    void connectClients() {
        payments = TestClients.payments(PAYMENT_API.baseUrl(), sleeper);
        shipping = TestClients.shipping(SHIPPING_API.baseUrl());
        checkout = new CheckoutService(payments, shipping);
    }
}
