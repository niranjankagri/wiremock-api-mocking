package com.niranjankagri.checkout.stubs;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.niranjankagri.checkout.support.TestClients;
import java.util.List;

/**
 * The payment provider as the tests see it: what it should do next, and what it received. This is the page object
 * of API mocking: paths, headers and WireMock calls live here, and tests read as behaviour.
 */
public final class PaymentProviderStub {

    private static final String PATH = "/v1/payments";
    private static final String SCENARIO = "payment recovers";
    private static final String JSON = "application/json";
    // Response templating: the answer is built from the request, as the real provider would
    private static final String CAPTURED_BODY = "{\"id\":\"pay_{{jsonPath request.body '$.orderId'}}\",\"status\":\"captured\"}";

    private final WireMockExtension api;

    /** @param api the WireMock server playing the provider */
    public PaymentProviderStub(WireMockExtension api) {
        this.api = api;
    }

    /** Accepts every well-formed charge. */
    public void capturesPayments() {
        api.stubFor(charge().willReturn(captured()));
    }

    /** Takes this long to capture a payment. @param millis the delay */
    public void capturesAfter(int millis) {
        api.stubFor(charge().willReturn(captured().withFixedDelay(millis)));
    }

    /** Refuses the card. @param code the provider's reason */
    public void declines(String code) {
        api.stubFor(charge().willReturn(json(402, "{\"code\":\"" + code + "\"}")));
    }

    /** Answers with this status and an empty body. @param status the HTTP status */
    public void answers(int status) {
        api.stubFor(charge().willReturn(aResponse().withStatus(status)));
    }

    /** Answers a charge with a success code and this body. @param body the body */
    public void answersCreatedWith(String body) {
        api.stubFor(charge().willReturn(json(201, body)));
    }

    /** Refuses every request for a wrong API key, whatever else it holds. */
    public void refusesTheApiKey() {
        api.stubFor(post(urlPathEqualTo(PATH)).willReturn(json(401, "{\"code\":\"invalid_api_key\"}")));
    }

    /** Rate-limits one request, then captures. @param retryAfter the {@code Retry-After} header's value */
    public void rateLimitsOnceThenCaptures(String retryAfter) {
        failThenCapture(1, aResponse().withStatus(429).withHeader("Retry-After", retryAfter));
    }

    /** Fails with 503 this many times, then captures. @param times the number of failures */
    public void unavailableThenCaptures(int times) {
        failThenCapture(times, aResponse().withStatus(503));
    }

    /** Breaks the connection on every charge. @param fault how it breaks */
    public void breaksConnection(Fault fault) {
        api.stubFor(charge().willReturn(aResponse().withFault(fault)));
    }

    /** Breaks the connection once, then captures. @param fault how it breaks */
    public void breaksConnectionOnceThenCaptures(Fault fault) {
        failThenCapture(1, aResponse().withFault(fault));
    }

    /** The first charge creates a payment (201), a repeat with the same key returns that payment (200). */
    public void remembersIdempotencyKeys() {
        api.stubFor(charge().inScenario("idempotent").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(captured()).willSetStateTo("charged"));
        api.stubFor(charge().inScenario("idempotent").whenScenarioStateIs("charged")
                .willReturn(captured().withStatus(200)));
    }

    /** @return the number of charge requests received */
    public int chargeCount() {
        return charges().size();
    }

    /** @return the {@code Idempotency-Key} of each request, in order */
    public List<String> idempotencyKeys() {
        return charges().stream().map(request -> request.getHeader("Idempotency-Key")).toList();
    }

    /** @return the URL of each request, in order */
    public List<String> chargeUrls() {
        return charges().stream().map(LoggedRequest::getUrl).toList();
    }

    /** Fails unless exactly one request had this JSON body. @param json the expected body */
    public void verifyChargedExactly(String json) {
        api.verify(1, postRequestedFor(urlPathEqualTo(PATH)).withRequestBody(equalToJson(json)));
    }

    // A charge only matches when it is well formed. Anything else is unmatched, and fails the test: see MockedApisTest.
    private static MappingBuilder charge() {
        return post(urlPathEqualTo(PATH))
                .withHeader("Authorization", equalTo("Bearer " + TestClients.PAYMENT_API_KEY))
                .withHeader("Content-Type", containing(JSON))
                .withHeader("Idempotency-Key", matching(".+"));
    }

    private void failThenCapture(int failures, ResponseDefinitionBuilder failure) {
        String state = Scenario.STARTED;
        for (int i = 1; i <= failures; i++) {
            String next = "failed " + i;
            api.stubFor(charge().inScenario(SCENARIO).whenScenarioStateIs(state)
                    .willReturn(failure).willSetStateTo(next));
            state = next;
        }
        api.stubFor(charge().inScenario(SCENARIO).whenScenarioStateIs(state).willReturn(captured()));
    }

    private static ResponseDefinitionBuilder captured() {
        return json(201, CAPTURED_BODY).withTransformers("response-template");
    }

    private static ResponseDefinitionBuilder json(int status, String body) {
        return aResponse().withStatus(status).withHeader("Content-Type", JSON).withBody(body);
    }

    private List<LoggedRequest> charges() {
        return api.findAll(postRequestedFor(urlPathEqualTo(PATH)));
    }
}
