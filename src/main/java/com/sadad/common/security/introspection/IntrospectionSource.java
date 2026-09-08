package com.sadad.common.security.introspection;

/** Which {@link TokenIntrospector} produced an {@link IntrospectionResult} - surfaced so
 * callers/logs can tell a fast local check apart from a live call to the issuing service. */
public enum IntrospectionSource {
    OFFLINE,
    ONLINE
}
