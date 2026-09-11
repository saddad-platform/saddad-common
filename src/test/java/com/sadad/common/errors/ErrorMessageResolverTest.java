package com.sadad.common.errors;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Choosing the sentence a particular reader sees, and the order the three sources are
 * consulted in.
 */
class ErrorMessageResolverTest {

    private ErrorMessageResolver withCatalogue(Map<String, ErrorCatalogCache.Translation> rows) {
        ErrorCatalogCache cache = new ErrorCatalogCache(Duration.ofMinutes(5), () -> rows);
        cache.refreshNow();
        return new ErrorMessageResolver(cache);
    }

    private final ErrorMessageResolver noCatalogue = withCatalogue(Map.of());

    @Test
    void answersInArabicWhenArabicWasAskedFor() {
        String message = noCatalogue.resolve("AUTH_INVALID_CREDENTIALS", "ar", Map.of(), "fallback");

        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS.getDefaultMessageAr(), message);
        assertTrue(message.codePoints().anyMatch(cp -> cp >= 0x0600 && cp <= 0x06FF));
    }

    @Test
    void answersInEnglishForEnglishAndForAnythingUnrecognised() {
        String en = ErrorCode.AUTH_INVALID_CREDENTIALS.getDefaultMessageEn();

        assertEquals(en, noCatalogue.resolve("AUTH_INVALID_CREDENTIALS", "en", Map.of(), "fallback"));
        assertEquals(en, noCatalogue.resolve("AUTH_INVALID_CREDENTIALS", "fr", Map.of(), "fallback"));
        assertEquals(en, noCatalogue.resolve("AUTH_INVALID_CREDENTIALS", null, Map.of(), "fallback"));
    }

    @Test
    void acceptsAFullLanguageTagAsWellAsABareCode() {
        // Browsers send "ar-SA,ar;q=0.9". The request filter narrows that to "ar", but the
        // resolver must not fall over if a full tag reaches it from somewhere else.
        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS.getDefaultMessageAr(),
                noCatalogue.resolve("AUTH_INVALID_CREDENTIALS", "ar-SA", Map.of(), "fallback"));
    }

    @Test
    void fillsInTheValuesTheExceptionCarried() {
        String message = noCatalogue.resolve("WALLET_INSUFFICIENT_FUNDS", "en",
                Map.of("reference", "VIO-2026-000003"), "fallback");

        assertTrue(message.contains("VIO-2026-000003"), message);
        assertFalse(message.contains("{reference}"), message);
    }

    @Test
    void putsTheSameValueWhereArabicPutsIt() {
        // The point of named parameters: the value lands wherever each language needs it,
        // rather than being welded to English word order by string concatenation.
        String arabic = noCatalogue.resolve("WALLET_INSUFFICIENT_FUNDS", "ar",
                Map.of("reference", "VIO-2026-000003"), "fallback");

        assertTrue(arabic.contains("VIO-2026-000003"), arabic);
        assertTrue(arabic.codePoints().anyMatch(cp -> cp >= 0x0600 && cp <= 0x06FF));
    }

    @Test
    void anAdministratorsEditWinsOverTheBuiltInText() {
        ErrorMessageResolver resolver = withCatalogue(Map.of(
                "AUTH_INVALID_CREDENTIALS", new ErrorCatalogCache.Translation(
                        "AUTH_INVALID_CREDENTIALS", "Edited English", "النص المعدّل", 401)));

        assertEquals("Edited English", resolver.resolve("AUTH_INVALID_CREDENTIALS", "en", Map.of(), "f"));
        assertEquals("النص المعدّل", resolver.resolve("AUTH_INVALID_CREDENTIALS", "ar", Map.of(), "f"));
    }

    @Test
    void anEditedMessageStillGetsItsValues() {
        ErrorMessageResolver resolver = withCatalogue(Map.of(
                "WALLET_INSUFFICIENT_FUNDS", new ErrorCatalogCache.Translation(
                        "WALLET_INSUFFICIENT_FUNDS", "Top up to pay {reference}", "اشحن لدفع {reference}", 422)));

        assertEquals("Top up to pay VIO-1", resolver.resolve(
                "WALLET_INSUFFICIENT_FUNDS", "en", Map.of("reference", "VIO-1"), "f"));
    }

    @Test
    void aHalfFilledRowFallsBackToTheOtherLanguageRatherThanGoingBlank() {
        // An administrator saving an English correction without touching Arabic must not
        // produce an empty message bubble for Arabic readers.
        ErrorMessageResolver resolver = withCatalogue(Map.of(
                "AUTH_INVALID_CREDENTIALS", new ErrorCatalogCache.Translation(
                        "AUTH_INVALID_CREDENTIALS", "Only English was edited", "  ", 401)));

        assertEquals("Only English was edited",
                resolver.resolve("AUTH_INVALID_CREDENTIALS", "ar", Map.of(), "f"));
    }

    @Test
    void anUnknownCodeFallsBackToWhateverTheExceptionSaid() {
        // A code from a newer service, or a row added by hand. Untranslated, but true -
        // which beats an empty response or an exception thrown while reporting an exception.
        assertEquals("the original message",
                noCatalogue.resolve("SOMETHING_THIS_BUILD_HAS_NEVER_HEARD_OF", "ar",
                        Map.of(), "the original message"));
    }

    @Test
    void reportsTheStatusTheCatalogueGivesACode() {
        assertEquals(422, noCatalogue.httpStatusFor("WALLET_INSUFFICIENT_FUNDS"));
        assertNull(noCatalogue.httpStatusFor("NOT_A_CODE"));
    }

    @Test
    void aCatalogueThatCannotBeLoadedIsNotAnError() {
        // The whole point of the built-in text. A service that cannot reach saddad-admin
        // must still report errors in both languages.
        ErrorCatalogCache broken = new ErrorCatalogCache(Duration.ofMinutes(5), () -> {
            throw new IllegalStateException("saddad-admin is down");
        });
        broken.refreshNow();
        ErrorMessageResolver resolver = new ErrorMessageResolver(broken);

        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS.getDefaultMessageAr(),
                resolver.resolve("AUTH_INVALID_CREDENTIALS", "ar", Map.of(), "f"));
        assertEquals(0, broken.size());
    }
}
