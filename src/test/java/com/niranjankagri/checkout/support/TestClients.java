package com.niranjankagri.checkout.support;

import com.niranjankagri.checkout.ApiConfig;
import com.niranjankagri.checkout.PaymentClient;
import com.niranjankagri.checkout.RetryPolicy;
import com.niranjankagri.checkout.ShippingClient;
import com.niranjankagri.checkout.Sleeper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/** Builds the real clients, pointed at a WireMock server. Nothing in the client is replaced except the base URL. */
public final class TestClients {

    /** The key the payment stubs insist on. */
    public static final String PAYMENT_API_KEY = "test-payment-key";
    /** The key the shipping stubs insist on. */
    public static final String SHIPPING_API_KEY = "test-shipping-key";
    /** Long enough for a slow CI runner, short enough to keep the timeout tests quick. */
    public static final Duration TIMEOUT = Duration.ofSeconds(1);
    /** Three attempts, waiting 200 ms and then 400 ms, and never more than 30 s. */
    public static final RetryPolicy RETRY = new RetryPolicy(3, Duration.ofMillis(200), Duration.ofSeconds(30));

    private TestClients() {
    }

    /**
     * @param baseUrl the WireMock server's URL
     * @param sleeper what to do between attempts
     * @return a payment client
     */
    public static PaymentClient payments(String baseUrl, Sleeper sleeper) {
        return new PaymentClient(http(), new ApiConfig(URI.create(baseUrl), PAYMENT_API_KEY, TIMEOUT), RETRY, sleeper);
    }

    /**
     * @param baseUrl the WireMock server's URL
     * @return a shipping client
     */
    public static ShippingClient shipping(String baseUrl) {
        return new ShippingClient(http(), new ApiConfig(URI.create(baseUrl), SHIPPING_API_KEY, TIMEOUT));
    }

    private static HttpClient http() {
        return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    }
}
