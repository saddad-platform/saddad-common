package com.sadad.common.otp;

/**
 * What a one-time code is for.
 *
 * <p>Constants rather than an enum so that a service can introduce a flow without every
 * other service needing the new value to compile. The purpose is half of a challenge's
 * identity: a code issued for a password reset must never confirm a sign-in, or the weaker
 * of the two flows becomes the way into the stronger one.
 */
public final class OtpPurpose {

    public static final String SIGN_IN = "SIGN_IN";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
    public static final String ONBOARDING_MOBILE = "ONBOARDING_MOBILE";

    private OtpPurpose() {}
}
