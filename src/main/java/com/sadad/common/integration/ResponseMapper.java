package com.sadad.common.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Reads an upstream response according to configuration rather than compiled-in field names.
 *
 * <p><b>The problem.</b> {@code integration_endpoints} already made the URL, method, headers
 * and timeouts configurable, but every caller still ended with lines like
 * {@code body.get("crName")}. So half of an upstream contract lived in data and half lived
 * in Java, and the half in Java was the half that changes when a vendor renames a field.
 * Renaming {@code crName} meant a code change and a redeploy.
 *
 * <p><b>The mapping language</b>, deliberately small - it describes where a value is and
 * when it counts as true, and nothing else:
 *
 * <pre>
 * {
 *   "entityNameEn": { "path": "crName" },
 *   "cityEn":       { "path": "address.city" },
 *   "statusRaw":    { "path": "status", "fallbackPaths": ["crStatus"] },
 *   "active":       { "path": "status", "fallbackPaths": ["crStatus"], "truthy": ["active", "1"] },
 *   "firstOwner":   { "path": "owners[0].name", "default": "unknown" }
 * }
 * </pre>
 *
 * <ul>
 *   <li>{@code path} - dotted, with {@code [n]} for list elements.</li>
 *   <li>{@code fallbackPaths} - tried in order when {@code path} is absent. Real vendors are
 *       inconsistent about this; Wathq returns the registration state as {@code status} on
 *       one operation and {@code crStatus} on another.</li>
 *   <li>{@code truthy} - the values that mean yes, compared case-insensitively as text. A
 *       registry answering {@code "active"}, {@code "1"} and {@code true} for the same idea
 *       is normal, and deciding which of those is affirmative is a property of the vendor,
 *       not of our code.</li>
 *   <li>{@code default} - used when nothing matched.</li>
 * </ul>
 *
 * <p><b>What it deliberately is not.</b> Not JSONPath, not an expression language, not
 * anything that can compute. A configuration format that can run logic is a configuration
 * format that can be made to run someone else's logic, and this one is edited through an
 * admin screen. It can locate a value and recognise a value; that is the whole vocabulary.
 *
 * <p><b>Failure behaviour.</b> A malformed mapping yields an empty result and a warning,
 * never an exception. These run inside identity and licence checks whose callers are written
 * to fail closed - an empty result means "not verified", which is the safe answer. A mapper
 * that threw would turn a bad character in a configuration field into an outage.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResponseMapper {

    private final ObjectMapper objectMapper;

    /**
     * Applies {@code mappingJson} to {@code response}, returning the logical fields it
     * describes. An absent or unparseable mapping returns empty, which callers treat as
     * "fall back to the field names I was written with".
     */
    public MappedResponse map(Map<String, Object> response, String mappingJson) {
        if (response == null || mappingJson == null || mappingJson.isBlank()) {
            return MappedResponse.empty();
        }
        Map<String, Object> spec;
        try {
            spec = objectMapper.readValue(mappingJson, Map.class);
        } catch (Exception e) {
            log.warn("Ignoring an unreadable response mapping - falling back to built-in field names: {}",
                    e.getMessage());
            return MappedResponse.empty();
        }

        Map<String, Object> out = new java.util.LinkedHashMap<>();
        spec.forEach((logicalField, rule) -> {
            try {
                out.put(logicalField, applyRule(response, rule));
            } catch (RuntimeException e) {
                log.warn("Could not map field '{}': {}", logicalField, e.getMessage());
            }
        });
        return new MappedResponse(out);
    }

    private Object applyRule(Map<String, Object> response, Object rule) {
        // A bare string is shorthand for {"path": "..."} - the common case, and worth not
        // making an operator type the long form for.
        if (rule instanceof String path) {
            return readPath(response, path).orElse(null);
        }
        if (!(rule instanceof Map<?, ?> map)) return null;

        List<String> paths = new ArrayList<>();
        Object primary = map.get("path");
        if (primary instanceof String p) paths.add(p);
        if (map.get("fallbackPaths") instanceof List<?> fallbacks) {
            fallbacks.forEach(f -> { if (f instanceof String s) paths.add(s); });
        }

        Optional<Object> found = Optional.empty();
        for (String path : paths) {
            found = readPath(response, path);
            if (found.isPresent()) break;
        }

        Object value = found.orElse(map.get("default"));
        if (map.get("truthy") instanceof List<?> truthy) {
            return isTruthy(value, truthy);
        }
        return value;
    }

    /** Text comparison, because a registry may answer {@code "1"}, {@code "active"} or a real
     * JSON boolean for the same idea, sometimes on different operations of the same API. */
    private boolean isTruthy(Object value, List<?> truthy) {
        if (value == null) return false;
        String actual = String.valueOf(value).trim();
        return truthy.stream()
                .filter(java.util.Objects::nonNull)
                .anyMatch(expected -> String.valueOf(expected).trim().equalsIgnoreCase(actual));
    }

    /** Walks {@code a.b[0].c}. Returns empty for anything missing or the wrong shape, which
     * is a normal answer here rather than an error. */
    private Optional<Object> readPath(Object root, String path) {
        Object current = root;
        for (String rawSegment : path.split("\\.")) {
            if (current == null || rawSegment.isEmpty()) return Optional.empty();

            String name = rawSegment;
            List<Integer> indexes = new ArrayList<>();
            int bracket = rawSegment.indexOf('[');
            if (bracket >= 0) {
                name = rawSegment.substring(0, bracket);
                for (String part : rawSegment.substring(bracket).split("\\[")) {
                    String digits = part.replace("]", "").trim();
                    if (digits.isEmpty()) continue;
                    try {
                        indexes.add(Integer.parseInt(digits));
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                }
            }

            if (!name.isEmpty()) {
                if (!(current instanceof Map<?, ?> map)) return Optional.empty();
                if (!map.containsKey(name)) return Optional.empty();
                current = map.get(name);
            }
            for (int index : indexes) {
                if (!(current instanceof List<?> list) || index < 0 || index >= list.size()) {
                    return Optional.empty();
                }
                current = list.get(index);
            }
        }
        return Optional.ofNullable(current);
    }

    /**
     * The logical fields a mapping produced, with the small amount of type coercion the
     * callers actually need.
     *
     * <p>{@link #isEmpty()} is how a caller tells "this endpoint has no mapping configured"
     * from "the mapping ran and found nothing", so it can fall back to its built-in field
     * names for the first and report a failed check for the second.
     */
    public record MappedResponse(Map<String, Object> fields) {

        public static MappedResponse empty() {
            return new MappedResponse(Map.of());
        }

        public boolean isEmpty() {
            return fields.isEmpty();
        }

        public boolean has(String field) {
            return fields.get(field) != null;
        }

        /** The value as text, or null. */
        public String text(String field) {
            Object value = fields.get(field);
            return value == null ? null : String.valueOf(value);
        }

        /** The value as text, or {@code fallback} when the mapping did not produce one. */
        public String textOr(String field, String fallback) {
            String value = text(field);
            return value == null || value.isBlank() ? fallback : value;
        }

        /**
         * True only for a value the mapping recognised as affirmative.
         *
         * <p>Anything unrecognised is false, never an exception - these decide whether an
         * identity or a licence check passed, and an unreadable answer must be treated as
         * "not verified" rather than allowed to propagate as a failure that some caller
         * might handle by continuing.
         */
        public boolean bool(String field) {
            Object value = fields.get(field);
            if (value instanceof Boolean b) return b;
            if (value == null) return false;
            String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
            return "true".equals(text) || "1".equals(text) || "yes".equals(text) || "active".equals(text);
        }
    }
}
