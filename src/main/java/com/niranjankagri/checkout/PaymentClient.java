package com.niranjankagri.checkout;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.Set;

/**
 * Client of the payment provider's API.
 *
 * <p>Every attempt for an order carries the same {@code Idempotency-Key}, so the provider charges once even when a
 * response got lost and the client tried again. That is what makes retrying a payment safe.
 */
public final class PaymentClient {

    private static final String CHARGES_PATH = "/v1/payments";
    // Answers that can change on the next attempt. Other 4xx answers will not: the same request gets the same refusal.
    private static final Set<Integer> RETRYABLE = Set.of(429, 500, 502, 503, 504);

    private final HttpClient http;
    private final ApiConfig config;
    private final RetryPolicy retryPolicy;
    private final Sleeper sleeper;

    /**
     * @param http        the HTTP client
     * @param config      the provider's address, key and timeout
     * @param retryPolicy how often and how long apart to try
     * @param sleeper     how to wait between attempts
     */
    public PaymentClient(HttpClient http, ApiConfig config, RetryPolicy retryPolicy, Sleeper sleeper) {
        this.http = http;
        this.config = config;
        this.retryPolicy = retryPolicy;
        this.sleeper = sleeper;
    }

    /**
     * Charges a card.
     *
     * @param charge what to charge
     * @return captured, declined, or unknown when no clear answer came after every attempt
     * @throws PaymentRejectedException if the provider refuses the request itself (wrong key, invalid data)
     */
    public PaymentOutcome charge(Charge charge) {
        // Built once: every attempt sends the identical request, idempotency key included
        HttpRequest request = chargeRequest(charge);
        String lastProblem = "no attempt made";
        try {
            for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
                Optional<String> retryAfter = Optional.empty();
                try {
                    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                    if (!RETRYABLE.contains(response.statusCode())) {
                        // A final answer, good or bad: stop trying
                        return read(response);
                    }
                    lastProblem = "HTTP " + response.statusCode();
                    retryAfter = response.headers().firstValue("Retry-After");
                } catch (IOException e) {
                    // A timeout or a broken connection says nothing about whether the provider took the money
                    lastProblem = e.getClass().getSimpleName();
                }
                // No wait after the last attempt: nothing follows it
                if (attempt < retryPolicy.maxAttempts()) {
                    sleeper.sleep(retryPolicy.delayAfter(attempt, retryAfter));
                }
            }
        } catch (InterruptedException e) {
            // Keep the flag set for the caller; the payment's state is as unclear as after a timeout
            Thread.currentThread().interrupt();
            return PaymentOutcome.unknown("interrupted");
        }
        return PaymentOutcome.unknown(lastProblem);
    }

    private HttpRequest chargeRequest(Charge charge) {
        return HttpRequest.newBuilder(config.baseUrl().resolve(CHARGES_PATH))
                .timeout(config.timeout())
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", charge.orderId())
                .POST(HttpRequest.BodyPublishers.ofString(JsonBodies.write(charge)))
                .build();
    }

    private static PaymentOutcome read(HttpResponse<String> response) {
        int status = response.statusCode();
        return switch (status) {
            // 200: the provider's answer to a repeated idempotency key; 201: a new payment
            case 200, 201 -> readPayment(response.body());
            case 402 -> PaymentOutcome.declined(readDeclineCode(response.body()));
            // The body is left out on purpose: it can echo request data into the logs
            default -> throw new PaymentRejectedException("The payment provider refused the request: HTTP " + status);
        };
    }

    private static PaymentOutcome readPayment(String body) {
        Optional<JsonNode> json = JsonBodies.parse(body);
        String id = json.map(node -> node.path("id").asText("")).orElse("");
        String state = json.map(node -> node.path("status").asText("")).orElse("");
        // A success code with an unreadable body must not count as paid, nor as failed
        if (id.isEmpty() || !"captured".equals(state)) {
            return PaymentOutcome.unknown("unreadable payment response");
        }
        return PaymentOutcome.captured(id);
    }

    private static String readDeclineCode(String body) {
        // A 402 is a clear refusal even when its reason cannot be read
        return JsonBodies.parse(body)
                .map(node -> node.path("code").asText(""))
                .filter(code -> !code.isEmpty())
                .orElse("unknown");
    }
}
