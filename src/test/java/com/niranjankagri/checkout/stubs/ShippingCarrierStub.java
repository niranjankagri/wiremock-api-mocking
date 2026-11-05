package com.niranjankagri.checkout.stubs;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.niranjankagri.checkout.support.TestClients;
import java.util.List;

/** The shipping carrier as the tests see it. Same idea as {@link PaymentProviderStub}. */
public final class ShippingCarrierStub {

    private static final String PATH = "/v1/rates";

    private final WireMockExtension api;

    /** @param api the WireMock server playing the carrier */
    public ShippingCarrierStub(WireMockExtension api) {
        this.api = api;
    }

    /** Quotes this price for any well-formed request. @param cents the price */
    public void quotes(long cents) {
        answersWith("{\"priceCents\":" + cents + ",\"carrier\":\"DHL\"}");
    }

    /** Quotes this price for exactly this postcode and weight. */
    public void quotesFor(String postcode, int weightGrams, long cents) {
        api.stubFor(rates()
                .withQueryParam("postcode", equalTo(postcode))
                .withQueryParam("weightGrams", equalTo(String.valueOf(weightGrams)))
                .willReturn(aResponse().withStatus(200).withBody("{\"priceCents\":" + cents + "}")));
    }

    /** Answers 200 with this body. @param body the body */
    public void answersWith(String body) {
        api.stubFor(rates().willReturn(aResponse().withStatus(200).withBody(body)));
    }

    /** Answers with this status. @param status the HTTP status */
    public void answers(int status) {
        api.stubFor(rates().willReturn(aResponse().withStatus(status)));
    }

    /** Breaks the connection. @param fault how it breaks */
    public void breaksConnection(Fault fault) {
        api.stubFor(rates().willReturn(aResponse().withFault(fault)));
    }

    /** Answers later than the client waits. @param millis the delay */
    public void quotesAfter(int millis) {
        api.stubFor(rates().willReturn(aResponse().withStatus(200).withBody("{\"priceCents\":1}")
                .withFixedDelay(millis)));
    }

    /** @return the number of rate requests received */
    public int requestCount() {
        return api.findAll(getRequestedFor(urlPathEqualTo(PATH))).size();
    }

    /** @return the raw URL of each request, before WireMock decodes it */
    public List<String> requestUrls() {
        return api.findAll(getRequestedFor(urlPathEqualTo(PATH))).stream().map(LoggedRequest::getUrl).toList();
    }

    private static MappingBuilder rates() {
        return get(urlPathEqualTo(PATH))
                .withHeader("X-Api-Key", equalTo(TestClients.SHIPPING_API_KEY))
                .withQueryParam("postcode", matching(".+"))
                .withQueryParam("weightGrams", matching("\\d+"));
    }
}
