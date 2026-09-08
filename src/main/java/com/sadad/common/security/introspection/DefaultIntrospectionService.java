package com.sadad.common.security.introspection;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Dispatches to {@link OfflineTokenIntrospector} or {@link OnlineTokenIntrospector} per
 * call. Falls back to OFFLINE when ONLINE is requested but no {@link OnlineTokenIntrospector}
 * bean exists (i.e. {@code sadad.introspection.online.base-url} was never configured) -
 * this keeps every service functional by default without forcing every deployment to wire
 * up online introspection before it has anywhere to point it at.
 */
@Slf4j
@Service
public class DefaultIntrospectionService implements IntrospectionService {

    private final OfflineTokenIntrospector offlineTokenIntrospector;
    private final Optional<OnlineTokenIntrospector> onlineTokenIntrospector;

    public DefaultIntrospectionService(OfflineTokenIntrospector offlineTokenIntrospector,
                                        Optional<OnlineTokenIntrospector> onlineTokenIntrospector) {
        this.offlineTokenIntrospector = offlineTokenIntrospector;
        this.onlineTokenIntrospector = onlineTokenIntrospector;
    }

    @Override
    public IntrospectionResult introspect(String token, IntrospectionMode mode) {
        if (mode == IntrospectionMode.ONLINE) {
            if (onlineTokenIntrospector.isPresent()) {
                return onlineTokenIntrospector.get().introspect(token);
            }
            log.warn("[INTROSPECTION] ONLINE mode requested but sadad.introspection.online.base-url "
                    + "is not configured - falling back to OFFLINE for this call.");
        }
        return offlineTokenIntrospector.introspect(token);
    }
}
