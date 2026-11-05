package com.niranjankagri.checkout;

import java.net.URI;
import java.time.Duration;

/**
 * Where a third-party API lives and how long to wait for it.
 *
 * @param baseUrl the API's root URL
 * @param apiKey  the credential sent with every request
 * @param timeout the longest wait for one response
 */
public record ApiConfig(URI baseUrl, String apiKey, Duration timeout) {
}
