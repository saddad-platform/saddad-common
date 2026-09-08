package com.sadad.common.security;

/**
 * Tracks the single currently-valid session per user so a new sign-in from any client
 * (web or mobile) invalidates whatever session was active before it. {@link
 * JwtAuthenticationFilter} consults this (when a bean is present) to reject requests
 * carrying a superseded session id, even though the JWT itself is still validly signed
 * and unexpired - this is what makes "sign out other sessions on new login" possible on
 * top of an otherwise-stateless JWT scheme.
 */
public interface SessionRegistry {

    /** Generates and stores a new active session id for the user, replacing any previous one. */
    String rotate(String userId);

    /** True if the given session id is still the user's current one. */
    boolean isCurrent(String userId, String sessionId);

    /** Invalidates the user's active session (explicit sign-out), so its token is rejected even though still validly signed and unexpired. */
    void invalidate(String userId);
}
