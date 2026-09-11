package com.sadad.common.security;

import com.sadad.common.errors.ErrorCode;
import com.sadad.common.exception.ValidationException;

import java.util.Map;

/**
 * The one password rule for every account a person signs into with a password.
 *
 * <p>It used to be written twice - once where onboarding sets the first password and once
 * where a forgotten one is reset - and a rule that exists in two places is two rules the
 * moment one of them is edited. It lives here, in the shared library, so the credentials
 * step, a reset, and a change from inside a session all hold a password to exactly the same
 * standard, and so the portal can ask what that standard is ({@link #describe()}) and show
 * it as a live checklist rather than a sentence of prose.
 *
 * <p>Letter case is judged with {@link Character#isUpperCase} and {@link Character#isLowerCase},
 * which is what the existing rule did. A script with no letter case (Arabic, for one) cannot
 * satisfy those two on its own, so a password in such a script needs Latin letters as well -
 * a known property of this rule, kept rather than quietly relaxed.
 */
public final class PasswordPolicy {

    /** The minimum length. Reported to callers through the error's {@code minLength} token. */
    public static final int MIN_LENGTH = 10;

    /** What the rule requires, in a shape the portal can render. */
    public record Description(int minLength, boolean uppercase, boolean lowercase, boolean digit, boolean symbol) {}

    private PasswordPolicy() {}

    public static Description describe() {
        return new Description(MIN_LENGTH, true, true, true, true);
    }

    public static boolean isStrong(String password) {
        if (password == null || password.length() < MIN_LENGTH) return false;
        return password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isDigit)
                && !password.chars().allMatch(Character::isLetterOrDigit);
    }

    /** Refuses a password the rule does not accept, naming the minimum length in the error. */
    public static void assertStrong(String password) {
        if (!isStrong(password)) {
            throw new ValidationException(ErrorCode.AUTH_PASSWORD_TOO_WEAK, Map.of("minLength", MIN_LENGTH));
        }
    }
}
