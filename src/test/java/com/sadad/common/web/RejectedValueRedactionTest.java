package com.sadad.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A validation failure returns the value that was rejected, which is helpful for a mistyped
 * IBAN and dangerous for a credential: the value was real, and the response body travels
 * through the browser's network tab, any proxy in between and any error monitor watching
 * failed requests.
 *
 * <p>Found by submitting an over-long vendor credential to the integrations console and
 * reading it back out of the 400.
 */
class RejectedValueRedactionTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(null);

    private Object redact(String field, Object value) {
        return ReflectionTestUtils.invokeMethod(handler, "safeRejectedValue", field, value);
    }

    @Test
    void aCredentialIsNeverEchoedBack() {
        assertEquals("[redacted]", redact("credential", "real-wathq-key-abc123"));
        assertEquals("[redacted]", redact("password", "Hunter2!"));
        assertEquals("[redacted]", redact("newPassword", "Hunter2!"));
        assertEquals("[redacted]", redact("otp", "123456"));
        assertEquals("[redacted]", redact("subscriptionKey", "abc"));
        assertEquals("[redacted]", redact("apiKey", "abc"));
        assertEquals("[redacted]", redact("smtpPassword", "abc"));
    }

    @Test
    void theMatchIsCaseInsensitiveAndMatchesAnywhereInTheName() {
        // A fail-closed list is only fail-closed if it catches the shapes people actually use.
        assertEquals("[redacted]", redact("CREDENTIAL", "x"));
        assertEquals("[redacted]", redact("provider.apiSecret", "x"));
        assertEquals("[redacted]", redact("request.accessToken", "x"));
    }

    @Test
    void anOrdinaryFieldStillSaysWhatWasRejected() {
        // The whole reason the value is returned: "you typed SA03 8000..." is what makes a
        // validation message actionable.
        assertEquals("SA0380000000608010167519", redact("iban", "SA0380000000608010167519"));
        assertEquals(42, redact("timeoutMs", 42));
        assertNull(redact("iban", null));
    }

    @Test
    void aValueShapedLikeACardNumberIsRedactedWhateverTheFieldIsCalled() {
        // Found by a test proving a PAN could not be stored in the last-four field: the
        // validation rejected it correctly and then quoted it back in the error.
        //
        // The field-name list is fail-closed but not clairvoyant - a PAN submitted into a
        // field nobody anticipated is still a PAN - so the value is checked too.
        assertEquals("[redacted]", redact("lastFour", "4111111111111111"));
        assertEquals("[redacted]", redact("anythingAtAll", "4111 1111 1111 1111"));
        assertEquals("[redacted]", redact("note", "5555-5555-5555-4444"));

        // And things that merely look numeric are left alone: over-redacting makes a
        // validation message useless, which is its own kind of failure.
        assertEquals("1234", redact("lastFour", "1234"));
        assertEquals("0501234567", redact("mobileNumber", "0501234567"));
        assertEquals("SA0380000000608010167519", redact("iban", "SA0380000000608010167519"));
    }

    @Test
    void anEnormousRejectedValueIsTruncatedRatherThanReturnedWhole() {
        Object result = redact("notes", "x".repeat(5_000));
        String text = String.valueOf(result);
        assertTrue(text.length() < 300, "a rejected 5 KB string must not be echoed in full");
        assertTrue(text.contains("5000 characters"), "and it should say how long it really was");
    }
}
