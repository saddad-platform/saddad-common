package com.sadad.common.security.introspection;

/**
 * Per-call choice of how to validate a bearer token - deliberately not a single
 * service-wide setting, since most endpoints should stay fast/local (OFFLINE, zero
 * behavior change from today's inline {@code JwtTokenProvider} checks) while a handful of
 * revocation-sensitive or high-value endpoints can opt into ONLINE explicitly.
 */
public enum IntrospectionMode {
    /** Local signature + expiry check only, via {@link OfflineTokenIntrospector} - no
     * network call, cannot see revocation that happened after the token was issued. */
    OFFLINE,
    /** Live call to the token-issuing service's own introspection endpoint, via {@link
     * OnlineTokenIntrospector} - catches revoked/superseded sessions the token's own
     * signature and expiry can't reveal, at the cost of a network round-trip. */
    ONLINE
}
