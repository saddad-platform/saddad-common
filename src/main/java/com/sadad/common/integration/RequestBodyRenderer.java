package com.sadad.common.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a request body from a configured template, so the shape of an outgoing call is
 * data rather than a {@code Map.of(...)} in Java.
 *
 * <pre>
 * template: {"AppSid": "{secret}", "Recipient": "{mobileNumber}", "Body": "{body}"}
 * values:   secret=..., mobileNumber=0501234567, body=Your code is 1234
 * </pre>
 *
 * <p><b>Substitution happens after parsing, never before.</b> The template is parsed as JSON
 * first, and only then are string values that are exactly a {@code {placeholder}} replaced
 * with the supplied object. Pasting values into the template text and parsing afterwards
 * would be the same mistake as building SQL by concatenation: an SMS body containing a quote
 * or a backslash - an apostrophe in a company name is enough - would produce malformed JSON
 * at best, and at worst would let text from an applicant's own input add fields to a request
 * this platform sends to a vendor under its own credentials. Substituting into the parsed
 * tree makes that structurally impossible: a value can only ever land where a value already
 * was.
 *
 * <p>A placeholder with no supplied value is replaced with an empty string rather than left
 * as literal {@code {name}} text, which would otherwise be sent to the vendor as if it were
 * real content.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestBodyRenderer {

    private final ObjectMapper objectMapper;

    /**
     * Renders {@code template} with {@code values}, or returns {@code fallback} when there
     * is no template configured or it cannot be read.
     *
     * <p>Falling back rather than throwing keeps a mistyped template from taking out the
     * send path entirely; the caller's built-in body still works.
     */
    public Map<String, Object> render(String template, Map<String, Object> values, Map<String, Object> fallback) {
        if (template == null || template.isBlank()) return fallback;
        try {
            Object parsed = objectMapper.readValue(template, Object.class);
            Object substituted = substitute(parsed, values == null ? Map.of() : values);
            if (substituted instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                return typed;
            }
            log.warn("A request body template must be a JSON object - using the built-in body instead");
            return fallback;
        } catch (Exception e) {
            log.warn("Ignoring an unreadable request body template - using the built-in body instead: {}",
                    e.getMessage());
            return fallback;
        }
    }

    /** Walks the parsed tree, replacing whole-string placeholders. Nested objects and arrays
     * are handled so a template is not limited to a flat body. */
    private Object substitute(Object node, Map<String, Object> values) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), substitute(v, values)));
            return out;
        }
        if (node instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            list.forEach(item -> out.add(substitute(item, values)));
            return out;
        }
        if (node instanceof String text
                && text.length() > 2 && text.startsWith("{") && text.endsWith("}")
                && text.indexOf('{', 1) < 0) {
            String name = text.substring(1, text.length() - 1);
            Object value = values.get(name);
            // Empty rather than the literal "{name}", which would be sent to the vendor as
            // though an operator had meant to send that text.
            return value == null ? "" : value;
        }
        return node;
    }
}
