package com.sadad.common.errors;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills the {@code {named}} placeholders in a catalogue message from the values an
 * exception carried with it.
 *
 * <pre>
 *   "Your wallet is short by {shortfall} SAR"  +  {shortfall: 1250.00}
 *   -> "Your wallet is short by 1250.00 SAR"
 * </pre>
 *
 * <p><b>Named, not positional.</b> Arabic and English put the same facts in different
 * orders - "you need {amount} more" against "تحتاج إلى {amount} إضافية" is the easy case,
 * and a sentence with two numbers is the hard one. Positional arguments silently swap the
 * two when a translator reorders a sentence, producing a message that is grammatical and
 * wrong. A name cannot be reordered into the wrong slot.
 *
 * <p><b>A missing value leaves the placeholder alone rather than printing "null".</b> The
 * message then reads as obviously unfinished to whoever sees it, which is a bug report;
 * "you need null more" reads as a broken product to a customer.
 *
 * <p><b>Values are never re-scanned.</b> A substituted value that itself contains
 * {@code {something}} is left as literal text - the pass is single, over the template,
 * so no value can inject a placeholder that a later pass would resolve. These messages
 * carry things like company names and reference numbers that come from user input.
 */
public final class MessageInterpolator {

    /** A name, not an expression: letters, digits and underscore. Nothing here evaluates. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9_]*)}");

    private MessageInterpolator() {}

    public static String interpolate(String template, Map<String, ?> params) {
        if (template == null || template.isEmpty()) return template;
        if (params == null || params.isEmpty() || template.indexOf('{') < 0) return template;

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder(template.length() + 16);
        int last = 0;
        while (matcher.find()) {
            out.append(template, last, matcher.start());
            Object value = params.get(matcher.group(1));
            // The placeholder itself when unresolved - see the class javadoc.
            out.append(value == null ? matcher.group(0) : format(value));
            last = matcher.end();
        }
        out.append(template, last, template.length());
        return out.toString();
    }

    /** Every placeholder name a template uses, for validating a catalogue entry. */
    public static java.util.Set<String> placeholdersIn(String template) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        if (template == null) return names;
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) names.add(matcher.group(1));
        return names;
    }

    /**
     * Renders a value for a human.
     *
     * <p>Money is formatted to two decimals in Latin digits regardless of language.
     * Arabic on this platform is already pinned to Latin numerals everywhere else - see
     * the date formatting in both portals - because a reference number or an amount that
     * changes shape between languages cannot be read back to a support agent over the
     * phone, which is what these numbers are for.
     */
    private static String format(Object value) {
        if (value instanceof java.math.BigDecimal amount) {
            return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        }
        if (value instanceof Double || value instanceof Float) {
            return String.format(Locale.ROOT, "%.2f", ((Number) value).doubleValue());
        }
        return String.valueOf(value);
    }
}
