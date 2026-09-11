package com.sadad.common.integration;

/**
 * What an integration mode means, in one place.
 *
 * <p>Two vocabularies grew up for the same idea. The platform registry says {@code MOCK};
 * the onboarding service's own configuration tables say {@code SIMULATED}. Both mean "do
 * not call the real upstream", and every consumer that checked only one of them was one
 * merged provider row away from firing a live request at a vendor it has no key for -
 * which is exactly what happened when the two Wathq providers were combined.
 *
 * <p>Reconciled here rather than by rewriting live configuration rows, because either
 * spelling may already be stored in any environment and both must keep working.
 */
public final class IntegrationMode {

    public static final String MOCK = "MOCK";
    public static final String SIMULATED = "SIMULATED";
    public static final String SANDBOX = "SANDBOX";
    public static final String LIVE = "LIVE";

    private IntegrationMode() {}

    /** True when this mode must not reach the real upstream. */
    public static boolean isSimulated(String mode) {
        return MOCK.equalsIgnoreCase(mode) || SIMULATED.equalsIgnoreCase(mode);
    }

    /** True when a real call should be attempted. Null is treated as simulated: an unset
     * mode must never be read as permission to call a live government registry. */
    public static boolean callsUpstream(String mode) {
        return mode != null && !isSimulated(mode);
    }
}
