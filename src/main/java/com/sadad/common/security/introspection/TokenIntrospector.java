package com.sadad.common.security.introspection;

/** A single strategy for turning a raw bearer token into an {@link IntrospectionResult}.
 * Never throws for a malformed/expired/unreachable-service token - returns {@link
 * IntrospectionResult#inactive} instead, so callers always get a value to branch on. */
public interface TokenIntrospector {

    IntrospectionResult introspect(String token);

    IntrospectionSource source();
}
