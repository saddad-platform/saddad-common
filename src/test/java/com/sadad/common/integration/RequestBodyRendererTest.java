package com.sadad.common.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Building an outgoing request body from configuration, safely. */
class RequestBodyRendererTest {

    private final RequestBodyRenderer renderer = new RequestBodyRenderer(new ObjectMapper());

    private static final Map<String, Object> FALLBACK = Map.of("built", "in");

    @Test
    void substitutesPlaceholdersIntoTheTemplate() {
        var body = renderer.render(
                """
                {"AppSid": "{secret}", "Recipient": "{mobileNumber}", "Body": "{body}"}""",
                Map.of("secret", "k-123", "mobileNumber", "0501234567", "body", "Your code is 1234"),
                FALLBACK);

        assertEquals("k-123", body.get("AppSid"));
        assertEquals("0501234567", body.get("Recipient"));
        assertEquals("Your code is 1234", body.get("Body"));
    }

    @Test
    void leavesLiteralsAlone() {
        var body = renderer.render(
                """
                {"Channel": "sms", "Recipient": "{mobileNumber}"}""",
                Map.of("mobileNumber", "0501234567"), FALLBACK);

        assertEquals("sms", body.get("Channel"));
    }

    @Test
    void aValueContainingQuotesCannotBreakTheRequest() {
        // The reason substitution happens after parsing. Pasting this into the template text
        // would produce malformed JSON, or worse, a value that closes its own string and
        // adds a field to a request sent under this platform's vendor credentials.
        var body = renderer.render(
                """
                {"Body": "{body}"}""",
                Map.of("body", "Ali\\'s \"code\" is 1234, \\\\ok"), FALLBACK);

        assertEquals("Ali\\'s \"code\" is 1234, \\\\ok", body.get("Body"));
        assertEquals(1, body.size(), "no extra fields may appear");
    }

    @Test
    void aValueThatLooksLikeJsonIsStillJustAValue() {
        var body = renderer.render(
                """
                {"Body": "{body}"}""",
                Map.of("body", "{\"injected\": true}"), FALLBACK);

        assertEquals(1, body.size());
        assertEquals("{\"injected\": true}", body.get("Body"));
    }

    @Test
    void handlesNestedObjectsAndArrays() {
        var body = renderer.render(
                """
                {"message": {"to": ["{mobileNumber}"], "text": "{body}"}}""",
                Map.of("mobileNumber", "0501234567", "body", "hello"), FALLBACK);

        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) body.get("message");
        assertEquals(List.of("0501234567"), message.get("to"));
        assertEquals("hello", message.get("text"));
    }

    @Test
    void anUnsuppliedPlaceholderBecomesEmptyRatherThanLiteralBraceText() {
        // Sending the vendor the characters "{senderId}" would be worse than sending nothing.
        var body = renderer.render(
                """
                {"SenderID": "{senderId}"}""",
                Map.of(), FALLBACK);

        assertEquals("", body.get("SenderID"));
    }

    @Test
    void noTemplateMeansTheCallersOwnBody() {
        assertEquals(FALLBACK, renderer.render(null, Map.of(), FALLBACK));
        assertEquals(FALLBACK, renderer.render("", Map.of(), FALLBACK));
    }

    @Test
    void anUnreadableTemplateFallsBackInsteadOfBreakingTheSendPath() {
        assertEquals(FALLBACK, renderer.render("{not json", Map.of(), FALLBACK));
        assertEquals(FALLBACK, renderer.render("[\"an array, not an object\"]", Map.of(), FALLBACK));
    }

    @Test
    void rendersTheSeededUnifonicBodyExactlyAsTheJavaMapDid() {
        // The equivalence that makes V36 safe to ship: same inputs, same body, no code.
        var body = renderer.render(
                "{\"AppSid\":\"{secret}\",\"SenderID\":\"{senderId}\",\"Recipient\":\"{mobileNumber}\",\"Body\":\"{body}\"}",
                Map.of("secret", "app-sid", "senderId", "SADAD",
                       "mobileNumber", "0501234567", "body", "Your SADAD verification code is: 1234"),
                FALLBACK);

        assertEquals(Map.of("AppSid", "app-sid", "SenderID", "SADAD",
                        "Recipient", "0501234567", "Body", "Your SADAD verification code is: 1234"),
                body);
    }
}
