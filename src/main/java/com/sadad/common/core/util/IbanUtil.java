package com.sadad.common.core.util;

import java.math.BigInteger;

/**
 * ISO 13616 (MOD-97) IBAN checksum validation, scoped to Saudi IBANs (24 characters,
 * "SA" + 2 check digits + 20 numeric BBAN). Used both for an admin-entered fallback IBAN
 * (when a bank's IBAN-generation service isn't available) and to sanity-check a
 * bank-generated one before it's stored - previously nothing in this codebase validated
 * an IBAN at all, only masked it for display (see {@link MaskingUtil#maskIban}).
 */
public final class IbanUtil {

    private static final int SAUDI_IBAN_LENGTH = 24;
    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private IbanUtil() {}

    public static boolean isValidSaudiIban(String rawIban) {
        String iban = normalize(rawIban);
        if (iban.length() != SAUDI_IBAN_LENGTH || !iban.startsWith("SA")) return false;
        if (!iban.substring(2).chars().allMatch(Character::isDigit)) return false;
        return mod97Remainder(iban) == 1;
    }

    public static String normalize(String iban) {
        return iban == null ? "" : iban.replaceAll("\\s+", "").toUpperCase();
    }

    /** Move the first 4 characters to the end, replace letters with two-digit numbers
     * (A=10 .. Z=35), then compute the remainder of the resulting number mod 97 - a valid
     * IBAN's checksum remainder is always exactly 1. */
    private static int mod97Remainder(String iban) {
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder numeric = new StringBuilder(rearranged.length() * 2);
        for (char c : rearranged.toCharArray()) {
            numeric.append(Character.isDigit(c) ? c : String.valueOf(Character.getNumericValue(c)));
        }
        return new BigInteger(numeric.toString()).mod(NINETY_SEVEN).intValue();
    }
}
