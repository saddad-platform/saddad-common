package com.sadad.common.integration;

import com.sadad.common.persistence.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.util.UUID;

/**
 * One callable upstream operation, described entirely as data.
 *
 * <p>Lives in {@code saddad-common} because five services need it. It used to be copied
 * verbatim into four of them - saddad-admin, saddad-auth, saddad-wallet and
 * saddad-violations each carried its own class mapping this same table, along with its own
 * copy of the caller that reads it. Four copies of a schema mapping is four places to
 * forget when a column is added, and the copies had already drifted in their comments about
 * which of them was authoritative. saddad-admin still owns the CRUD; everyone else reads.
 *
 * <p><b>The URL is stored per environment, in full, including the path.</b> That is what
 * makes an upstream contract change a configuration change: when a vendor renames a path or
 * moves a version prefix, nothing is rebuilt. {@code {placeholder}} segments are substituted
 * from values the caller supplies.
 *
 * <p><b>Reading the response is configuration too</b>, via {@link #responseMappingJson}.
 * Without it, "the field is called crName" was a Java constant, so a renamed response field
 * was a code change and a redeploy - the exact thing this table exists to avoid, left
 * half-finished. See {@code ResponseMapper} for the mapping language.
 */
@Entity
@Table(name = "integration_endpoints")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationEndpoint extends BaseAuditableEntity {

    @Column(name = "provider_id", nullable = false)
    private UUID providerId;

    @Column(name = "endpoint_key", nullable = false)
    private String endpointKey;

    @Column(name = "module", nullable = false)
    private String module;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_ar", nullable = false)
    private String nameAr;

    @Column(name = "http_method", nullable = false)
    @Builder.Default
    private String httpMethod = "GET";

    @Column(name = "url_mock")
    private String urlMock;

    @Column(name = "url_sandbox")
    private String urlSandbox;

    @Column(name = "url_live")
    private String urlLive;

    @Column(name = "headers_json")
    private String headersJson;

    @Column(name = "query_params_json")
    private String queryParamsJson;

    @Column(name = "timeout_ms", nullable = false)
    @Builder.Default
    private int timeoutMs = 8000;

    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private int maxRetries = 1;

    /**
     * How to read this endpoint's response, as JSON - see {@code ResponseMapper}.
     *
     * <p>Maps the logical field a caller asks for onto where it actually lives in the
     * upstream payload, so a vendor renaming {@code crName} to {@code companyNameEn} is an
     * edit in the admin console rather than a release.
     *
     * <p>Null means "no mapping configured", and callers fall back to the field names they
     * were written with. That fallback is deliberate: it makes adopting this safe one
     * endpoint at a time instead of all at once.
     */
    @Column(name = "response_mapping_json")
    private String responseMappingJson;

    /**
     * The request body for endpoints that send one, as a template with {@code {placeholder}}
     * substitutions - the POST equivalent of putting the path in configuration.
     */
    @Column(name = "request_body_template")
    private String requestBodyTemplate;

    /**
     * How long a successful response may be reused, in minutes. Zero disables caching.
     *
     * <p>Per endpoint rather than per provider, because within one vendor the right answer
     * differs: a company's registered name is stable for days, while its licence status is
     * the thing being checked and must not be served from a cache.
     */
    @Column(name = "cache_ttl_minutes", nullable = false)
    @Builder.Default
    private int cacheTtlMinutes = 0;

    /**
     * <b>Superseded and unread.</b> Kept only so that existing rows are not silently
     * dropped on the next save.
     *
     * <p>It was meant to name which secret to inject, against a keyed secret store that was
     * never built - so nothing ever resolved it, and configuring it did nothing. A provider
     * now holds exactly one credential
     * ({@link IntegrationProviderConfig#getCredentialEncrypted()}), which removes the thing
     * this field was for. Do not write new code against it; the column is a candidate for
     * removal once no environment still has values in it.
     */
    @Column(name = "secret_ref")
    private String secretRef;

    /**
     * The header this endpoint's credential is sent in, e.g.
     * {@code Ocp-Apim-Subscription-Key}.
     *
     * <p>This is the half of the mechanism that is real: the credential lives on the
     * provider, because a vendor account is one account, and the header differs per
     * operation. {@code headers_json} is plain text and is returned to the console, so the
     * secret is never stored there - the caller injects it into this header at call time.
     */
    @Column(name = "secret_header_name")
    private String secretHeaderName;
}
