package com.niranjankagri.checkout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;

/** JSON for both clients. One mapper for all: it is thread-safe, and costly to create. */
final class JsonBodies {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonBodies() {
    }

    /**
     * Reads a response body.
     *
     * @param body the text the API sent
     * @return the parsed JSON, or empty when the text is not JSON: a third party's broken answer is an expected
     *         case for the callers, not an exception
     */
    static Optional<JsonNode> parse(String body) {
        try {
            return Optional.ofNullable(MAPPER.readTree(body));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    /**
     * Writes a request body.
     *
     * @param value the object to write
     * @return its JSON
     */
    static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            // Only a programming error gets here: every value written is a plain record
            throw new IllegalStateException("Could not write the request body", e);
        }
    }
}
