package com.sadad.common.errors;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the properties of the catalogue that nothing else can check.
 *
 * <p>A wrong error message is a silent defect: it compiles, it returns correctly shaped JSON
 * with the wrong words in it, and the only person who sees it is a customer who has already
 * hit a problem. These are the invariants that stop that.
 */
class ErrorCatalogIntegrityTest {

    @Test
    void everyCodeHasBothLanguages() {
        List<String> missing = new ArrayList<>();
        for (ErrorCode code : ErrorCode.values()) {
            if (code.getDefaultMessageEn() == null || code.getDefaultMessageEn().isBlank()) {
                missing.add(code.code() + " has no English text");
            }
            if (code.getDefaultMessageAr() == null || code.getDefaultMessageAr().isBlank()) {
                missing.add(code.code() + " has no Arabic text");
            }
        }
        assertTrue(missing.isEmpty(), "Every code must be readable in both languages: " + missing);
    }

    @Test
    void arabicAndEnglishCarryTheSameFacts() {
        // A placeholder present in one language and absent in the other is a fact silently
        // dropped for the readers of that language - "you need {amount} more" against
        // "you need more". It is invisible to anyone who does not read both.
        List<String> mismatched = new ArrayList<>();
        for (ErrorCode code : ErrorCode.values()) {
            Set<String> en = MessageInterpolator.placeholdersIn(code.getDefaultMessageEn());
            Set<String> ar = MessageInterpolator.placeholdersIn(code.getDefaultMessageAr());
            if (!en.equals(ar)) {
                mismatched.add(code.code() + " English=" + en + " Arabic=" + ar);
            }
        }
        assertTrue(mismatched.isEmpty(), "Both translations must use the same placeholders: " + mismatched);
    }

    @Test
    void declaredParametersMatchTheOnesTheMessagesActuallyUse() {
        // The declared list is what a caller reads to know what to pass. A message using a
        // placeholder nobody declared will never be filled in; a declared parameter no
        // message uses is a value quietly discarded.
        List<String> wrong = new ArrayList<>();
        for (ErrorCode code : ErrorCode.values()) {
            Set<String> used = MessageInterpolator.placeholdersIn(code.getDefaultMessageEn());
            Set<String> declared = Set.copyOf(code.getParams());
            if (!used.equals(declared)) {
                wrong.add(code.code() + " uses " + used + " but declares " + declared);
            }
        }
        assertTrue(wrong.isEmpty(), "Declared parameters must match the message: " + wrong);
    }

    @Test
    void everyCodeHasAPlausibleHttpStatus() {
        for (ErrorCode code : ErrorCode.values()) {
            assertTrue(code.getHttpStatus() >= 400 && code.getHttpStatus() <= 599,
                    code.code() + " has a non-error HTTP status: " + code.getHttpStatus());
        }
    }

    @Test
    void severityAndCategoryAreFromTheKnownSets() {
        // The admin console filters and colours by these; a typo produces a row that sorts
        // into a group of one and a badge with no styling.
        Set<String> severities = Set.of("INFO", "WARN", "ERROR", "CRITICAL");
        for (ErrorCode code : ErrorCode.values()) {
            assertTrue(severities.contains(code.getSeverity()),
                    code.code() + " has an unknown severity: " + code.getSeverity());
            assertFalse(code.getCategory().isBlank(), code.code() + " has no category");
        }
    }

    @Test
    void codesAreLookedUpByTheirWireName() {
        assertEquals(ErrorCode.WALLET_INSUFFICIENT_FUNDS,
                ErrorCode.find("WALLET_INSUFFICIENT_FUNDS").orElseThrow());
        assertTrue(ErrorCode.find("SOMETHING_A_NEWER_SERVICE_KNOWS").isEmpty());
        assertTrue(ErrorCode.find(null).isEmpty());
    }

    @Test
    void arabicIsActuallyArabic() {
        // Cheap, and it catches the one mistake that is easy to make and hard to see in a
        // 128-entry file: pasting the English text into the Arabic column.
        List<String> notArabic = new ArrayList<>();
        for (ErrorCode code : ErrorCode.values()) {
            boolean hasArabicLetters = code.getDefaultMessageAr().codePoints()
                    .anyMatch(cp -> cp >= 0x0600 && cp <= 0x06FF);
            if (!hasArabicLetters) notArabic.add(code.code());
        }
        assertTrue(notArabic.isEmpty(), "These have no Arabic characters in the Arabic column: " + notArabic);
    }

    @Test
    void aMessageThatNamesOurOwnDeploymentIsNotShownToCustomers() {
        // userSafe is about what a customer may read, not about how loudly operators are
        // told - severity covers that. A session expiry is CRITICAL and perfectly safe to
        // show; a message naming an unconfigured provider is neither.
        assertFalse(ErrorCode.INTEGRATION_NOT_CONFIGURED.isUserSafe());
        assertFalse(ErrorCode.SECRET_DECRYPTION_FAILED.isUserSafe());
        assertTrue(ErrorCode.SESSION_SUPERSEDED.isUserSafe(), "CRITICAL, and entirely safe to display");
        assertTrue(ErrorCode.ONB_MOBILE_NOT_OWNED.isUserSafe());
    }

    @Test
    void everyMessageAnApplicantCouldTriggerIsShowable() {
        // The onboarding and payment paths are the ones a member of the public reaches.
        // Suppressing one of those would leave them with a generic failure and no way
        // forward, which is worse than the leak the flag exists to prevent.
        for (ErrorCode code : ErrorCode.values()) {
            if (code.getCategory().equals("ONBOARDING") || code.getCategory().equals("PAYMENT")) {
                assertTrue(code.isUserSafe(), code.code() + " must be showable to an applicant");
            }
        }
    }
}
