package com.niranjankagri.checkout;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Client of the shipping carrier's rate API.
 *
 * <p>A shipping price is not worth losing a sale for: any failure gives the flat rate, marked as estimated, after one
 * attempt. A customer waiting at the checkout should not wait for retries.
 */
public final class ShippingClient {

    /** The price used when the carrier gives no usable answer. */
    public static final long FLAT_RATE_CENTS = 995;

    private final HttpClient http;
    private final ApiConfig config;

    /**
     * @param http   the HTTP client
     * @param config the carrier's address, key and timeout
     */
    public ShippingClient(HttpClient http, ApiConfig config) {
        this.http = http;
        this.config = config;
    }

    /**
     * Asks for a shipping price.
     *
     * @param postcode    the delivery postcode
     * @param weightGrams the parcel weight
     * @return the carrier's price, or the flat rate marked as estimated
     */
    public ShippingQuote quote(String postcode, int weightGrams) {
        try {
            HttpResponse<String> response = http.send(rateRequest(postcode, weightGrams),
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 ? readQuote(response.body()) : ShippingQuote.flatRate();
        } catch (IOException e) {
            return ShippingQuote.flatRate();
        } catch (InterruptedException e) {
            // Keep the flag set for the caller
            Thread.currentThread().interrupt();
            return ShippingQuote.flatRate();
        }
    }

    private HttpRequest rateRequest(String postcode, int weightGrams) {
        // URLEncoder writes a space as "+", which only form decoders read as a space; %20 works everywhere
        String encoded = URLEncoder.encode(postcode, StandardCharsets.UTF_8).replace("+", "%20");
        String path = "/v1/rates?postcode=" + encoded + "&weightGrams=" + weightGrams;
        return HttpRequest.newBuilder(config.baseUrl().resolve(path))
                .timeout(config.timeout())
                .header("X-Api-Key", config.apiKey())
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    private static ShippingQuote readQuote(String body) {
        return JsonBodies.parse(body)
                .map(json -> json.path("priceCents"))
                // A negative price would lower the total charged, so it counts as no answer
                .filter(price -> price.isIntegralNumber() && price.asLong() >= 0)
                .map(price -> new ShippingQuote(price.asLong(), false))
                .orElseGet(ShippingQuote::flatRate);
    }
}
