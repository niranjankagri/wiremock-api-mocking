package com.niranjankagri.checkout.support;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

/** Builds the WireMock servers, all with the same strict settings. */
public final class MockServers {

    private MockServers() {
    }

    /** @return a server on a free port, with no stubs yet */
    public static WireMockExtension empty() {
        return strict(wireMockConfig().dynamicPort());
    }

    /**
     * @param classpathRoot the folder holding {@code mappings} and {@code __files}
     * @return a server on a free port, with the stubs from these JSON files
     */
    public static WireMockExtension fromFiles(String classpathRoot) {
        return strict(wireMockConfig().dynamicPort().usingFilesUnderClasspath(classpathRoot));
    }

    private static WireMockExtension strict(WireMockConfiguration options) {
        return WireMockExtension.newInstance()
                .options(options)
                // A request no stub matches (wrong path, a missing header) fails the test, whatever it asserts
                .failOnUnmatchedRequests(true)
                .build();
    }
}
