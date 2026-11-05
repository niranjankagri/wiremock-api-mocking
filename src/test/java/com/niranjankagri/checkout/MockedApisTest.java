package com.niranjankagri.checkout;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.niranjankagri.checkout.stubs.PaymentProviderStub;
import com.niranjankagri.checkout.stubs.ShippingCarrierStub;
import com.niranjankagri.checkout.support.RecordingSleeper;
import com.niranjankagri.checkout.support.TestClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Life cycle of the tests: two WireMock servers on free ports, one per third-party API, and fresh real clients for
 * each test. WireMock resets stubs and its request log before every test.
 */
abstract class MockedApisTest {

    @RegisterExtension
    static final WireMockExtension PAYMENT_API = server();

    @RegisterExtension
    static final WireMockExtension SHIPPING_API = server();

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

    private static WireMockExtension server() {
        return WireMockExtension.newInstance()
                .options(wireMockConfig().dynamicPort())
                // A request no stub matches (wrong path, a missing header) fails the test, whatever it asserts
                .failOnUnmatchedRequests(true)
                .build();
    }
}
