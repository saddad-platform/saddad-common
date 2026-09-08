package com.sadad.common.core.util;

/**
 * Utility for masking sensitive data (PII, CR, National ID, IBAN, Email).
 * Adheres to saddad-docs/09-OBSERVABILITY-ERROR-HANDLING.md Section 2.
 */
public final class MaskingUtil {

    private MaskingUtil() {}

    public static String maskCr(String cr) {
        if (cr == null || cr.length() < 6) return "****";
        return cr.substring(0, 2) + "******" + cr.substring(cr.length() - 2);
    }

    public static String maskNationalId(String id) {
        if (id == null || id.length() < 6) return "****";
        return id.substring(0, 2) + "******" + id.substring(id.length() - 2);
    }

    public static String maskIban(String iban) {
        if (iban == null || iban.length() < 10) return "SA** **** **** ****";
        String clean = iban.replaceAll("\\s+", "");
        if (clean.length() >= 8) {
            String prefix = clean.substring(0, 4);
            String suffix = clean.substring(clean.length() - 4);
            return prefix + " **** **** **** **** " + suffix;
        }
        return "SA** ****";
    }

    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 6) return "****";
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 3);
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "****";
        int atIndex = email.indexOf('@');
        String name = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (name.length() <= 2) {
            return name.charAt(0) + "*" + domain;
        }
        return name.substring(0, 2) + "******" + domain;
    }
}