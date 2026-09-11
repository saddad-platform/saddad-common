package com.sadad.common.errors;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageInterpolatorTest {

    @Test
    void replacesNamedPlaceholders() {
        assertEquals("Pay VIO-1 now",
                MessageInterpolator.interpolate("Pay {reference} now", Map.of("reference", "VIO-1")));
    }

    @Test
    void replacesEveryOccurrenceOfTheSameName() {
        assertEquals("VIO-1 / VIO-1",
                MessageInterpolator.interpolate("{ref} / {ref}", Map.of("ref", "VIO-1")));
    }

    @Test
    void leavesAnUnsuppliedPlaceholderVisibleRatherThanPrintingNull() {
        // "you need null more" reads as a broken product to a customer; the unresolved
        // placeholder reads as an unfinished message, which is a bug report.
        assertEquals("You need {amount} more",
                MessageInterpolator.interpolate("You need {amount} more", Map.of("other", 1)));
    }

    @Test
    void doesNotRescanSubstitutedValues() {
        // Values carry company names and reference numbers that come from user input. A
        // value containing {something} must stay literal text, not become a placeholder a
        // later pass would resolve.
        assertEquals("Name: {reference}",
                MessageInterpolator.interpolate("Name: {name}",
                        Map.of("name", "{reference}", "reference", "SECRET")));
    }

    @Test
    void formatsMoneyToTwoDecimalsInLatinDigits() {
        assertEquals("1250.00 SAR", MessageInterpolator.interpolate(
                "{amount} SAR", Map.of("amount", new BigDecimal("1250"))));
        assertEquals("0.50 SAR", MessageInterpolator.interpolate(
                "{amount} SAR", Map.of("amount", new BigDecimal("0.499"))));
    }

    @Test
    void handlesTemplatesWithNothingToReplace() {
        assertEquals("No placeholders", MessageInterpolator.interpolate("No placeholders", Map.of("a", 1)));
        assertNull(MessageInterpolator.interpolate(null, Map.of()));
        assertEquals("{a}", MessageInterpolator.interpolate("{a}", null));
    }

    @Test
    void findsThePlaceholdersATemplateUses() {
        assertEquals(java.util.Set.of("from", "to"),
                MessageInterpolator.placeholdersIn("From {from} to {to}"));
        assertTrue(MessageInterpolator.placeholdersIn("nothing here").isEmpty());
    }

    @Test
    void ignoresBracesThatAreNotPlaceholderNames() {
        // JSON-ish text in a message must not be mistaken for a placeholder.
        assertEquals("{ }", MessageInterpolator.interpolate("{ }", Map.of("a", 1)));
        assertEquals("{1abc}", MessageInterpolator.interpolate("{1abc}", Map.of("1abc", "x")));
    }
}
