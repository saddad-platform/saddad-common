package com.sadad.common.errors;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** How this service reaches the error catalogue saddad-admin owns. */
@Getter
@Setter
@ConfigurationProperties(prefix = "sadad.error-catalog")
public class ErrorCatalogProperties {

    /**
     * Where saddad-admin serves the catalogue. Blank switches the remote catalogue off
     * entirely and leaves the service on its compiled-in wording, which is the correct
     * setting for saddad-admin itself - it owns the table and reads it directly.
     */
    private String url = "";

    /** The shared internal key; the catalogue endpoint is system-to-system, not user-facing. */
    private String apiKey = "";

    /**
     * How long a loaded catalogue is trusted before a background refresh is triggered.
     * Five minutes: an operator correcting a message expects it live within a coffee break,
     * and nothing here is worth a tighter poll across every service.
     */
    private Duration refreshInterval = Duration.ofMinutes(5);
}
