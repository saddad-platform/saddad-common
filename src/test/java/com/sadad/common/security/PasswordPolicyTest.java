package com.sadad.common.security;

import com.sadad.common.errors.ErrorCode;
import com.sadad.common.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    @Test
    void acceptsAPasswordThatMeetsEveryRequirement() {
        assertTrue(PasswordPolicy.isStrong("Rawabi#2026Adm"));
        assertTrue(PasswordPolicy.isStrong("Ab1!Ab1!Ab"));
    }

    @Test
    void refusesEachMissingRequirementOnItsOwn() {
        assertFalse(PasswordPolicy.isStrong("Ab1!Ab1!A"), "one character short");
        assertFalse(PasswordPolicy.isStrong("ab1!ab1!ab1!"), "no upper-case letter");
        assertFalse(PasswordPolicy.isStrong("AB1!AB1!AB1!"), "no lower-case letter");
        assertFalse(PasswordPolicy.isStrong("Abc!Abc!Abc!"), "no digit");
        assertFalse(PasswordPolicy.isStrong("Abc1Abc1Abc1"), "no symbol");
        assertFalse(PasswordPolicy.isStrong(null));
    }

    @Test
    void theRefusalNamesTheMinimumLength() {
        ValidationException refused = assertThrows(ValidationException.class,
                () -> PasswordPolicy.assertStrong("short"));
        assertEquals(ErrorCode.AUTH_PASSWORD_TOO_WEAK.name(), refused.getCode());
        assertEquals(PasswordPolicy.MIN_LENGTH, refused.getParams().get("minLength"));
    }

    @Test
    void describesItselfForTheChecklistThePortalShows() {
        PasswordPolicy.Description description = PasswordPolicy.describe();
        assertEquals(PasswordPolicy.MIN_LENGTH, description.minLength());
        assertTrue(description.uppercase() && description.lowercase() && description.digit() && description.symbol());
    }
}
