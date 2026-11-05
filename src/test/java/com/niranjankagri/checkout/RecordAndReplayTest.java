package com.niranjankagri.checkout;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.recordSpec;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.recording.SnapshotRecordResult;
import com.niranjankagri.checkout.support.RecordingSleeper;
import com.niranjankagri.checkout.support.TestClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Record and replay: WireMock sits between the client and a provider, keeps what it sees as stubs, and can answer
 * alone afterwards. A second WireMock stands in for the real provider, so the test needs no network.
 */
@DisplayName("Record and replay")
class RecordAndReplayTest {

    private final WireMockServer realProvider = new WireMockServer(wireMockConfig().dynamicPort());
    private final WireMockServer recorder = new WireMockServer(wireMockConfig().dynamicPort());

    @BeforeEach
    void startServers() {
        realProvider.start();
        recorder.start();
        // With a text Content-Type the recorded body stays in the stub; without one WireMock writes it to a file
        realProvider.stubFor(post(urlPathEqualTo("/v1/payments")).willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"pay_recorded\",\"status\":\"captured\"}")));
    }

    @AfterEach
    void stopServers() {
        recorder.stop();
        realProvider.stop();
    }

    @Test
    void tc171_aRecordedAnswerIsReplayedAfterTheProviderIsGone() {
        PaymentClient client = TestClients.payments(recorder.baseUrl(), new RecordingSleeper());

        recorder.startRecording(recordSpec().forTarget(realProvider.baseUrl()).makeStubsPersistent(false));
        PaymentOutcome recorded = client.charge("ORD-7", 1_000, "EUR", "tok_visa");
        SnapshotRecordResult stubs = recorder.stopRecording();
        realProvider.stop();

        PaymentOutcome replayed = client.charge("ORD-7", 1_000, "EUR", "tok_visa");

        assertThat(stubs.getStubMappings()).hasSize(1);
        assertThat(recorded.paymentId()).isEqualTo("pay_recorded");
        assertThat(replayed).isEqualTo(recorded);
    }

    @Test
    void tc172_aRequestThatWasNeverRecordedIsNotAnsweredAsIfItWere() {
        PaymentClient client = TestClients.payments(recorder.baseUrl(), new RecordingSleeper());

        recorder.startRecording(recordSpec().forTarget(realProvider.baseUrl()).makeStubsPersistent(false));
        client.charge("ORD-7", 1_000, "EUR", "tok_visa");
        recorder.stopRecording();

        // The recording matches on the body, so another amount is a different request: WireMock answers 404
        assertThatThrownBy(() -> client.charge("ORD-7", 2_000, "EUR", "tok_visa"))
                .isInstanceOf(PaymentRejectedException.class)
                .hasMessageContaining("HTTP 404");
    }
}
