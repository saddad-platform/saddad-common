package com.sadad.common.security.introspection;

/** The single bean every resource service wires to validate a bearer token - dispatches
 * to the appropriate {@link TokenIntrospector} per call, per {@link IntrospectionMode}. */
public interface IntrospectionService {

    IntrospectionResult introspect(String token, IntrospectionMode mode);

    /** Convenience for the overwhelming majority of endpoints, which should stay fast/local
     * and see zero behavior change from today's inline {@code JwtTokenProvider} checks. */
    default IntrospectionResult introspect(String token) {
        return introspect(token, IntrospectionMode.OFFLINE);
    }
}
