package com.sadad.common.mail;

import com.sadad.common.core.api.ApiResponse;
import com.sadad.common.core.util.MaskingUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * The one way any service sends transactional email: a call to saddad-admin, which owns the
 * SMTP configuration and the email templates and performs the actual send.
 *
 * <p>This replaces four near-identical copies of the same client (saddad-auth's,
 * saddad-wallet's and saddad-violations' {@code NotificationsClient}, plus
 * saddad-onboarding's own {@code MailClient}), each of which called a separate
 * saddad-notifications deployable that then had to call <em>back</em> to saddad-admin over
 * HTTP for the config and template it needed. That was two network hops and one reverse
 * dependency edge to send one email. saddad-notifications was merged into saddad-admin, so
 * the config lookup is now a local JPA read and this is the only remaining hop.
 *
 * <p><strong>Fail-open, always.</strong> Every method swallows failure and logs it. A
 * notification email must never be able to fail the sign-in, wallet top-up, settlement or
 * onboarding activation that triggered it - which is also why this is a plain component and
 * not part of any caller's transaction.
 *
 * <p>Recipient addresses are masked in logs ({@link MaskingUtil}); nothing here ever logs a
 * body, a token map or a credential.
 *
 * <p>Registered only where {@code sadad.mail.base-url} is configured. saddad-admin is the
 * service on the other end of that URL, so it has no such property and gets no bean - it
 * calls {@code MailSendingService} directly rather than making an HTTP request to itself.
 * Any other service that forgets the property fails loudly at wiring time, which is the
 * intended outcome: silently not sending mail is worse than not starting.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sadad.mail.base-url")
public class MailClient {

    private final RestClient restClient;

    public MailClient(@Value("${sadad.mail.base-url}") String mailBaseUrl,
                      @Value("${sadad.internal.api-key}") String internalApiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(10_000);

        this.restClient = RestClient.builder()
                .baseUrl(mailBaseUrl)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Renders the named template (owned and stored by saddad-admin, editable from the admin
     * console) with {@code tokens} and sends it. The template's own bilingual subject/body
     * are rendered and stacked in one message.
     */
    public void sendTemplate(String templateKey, String toEmail, Map<String, String> tokens) {
        post("/v1/internal/mail/send-template",
                new SendTemplateMailRequest(templateKey, toEmail, tokens),
                "template=" + templateKey, toEmail);
    }

    /**
     * Sends an already-rendered message through the shared SMTP connection - for a caller
     * that owns its own template content (saddad-onboarding renders its own bilingual
     * templates locally before handing the finished text over).
     *
     * <p>There is no From parameter. This used to take one so onboarding could show its own
     * sender identity on the shared connection, and onboarding was the only caller that ever
     * passed it. The platform has one sender identity now, held in saddad-admin's mail
     * configuration; leaving the parameter behind would have advertised a choice that no
     * longer exists.
     */
    public void send(String toEmail, String subject, String body) {
        send(toEmail, subject, body, false);
    }

    /**
     * @param html the body is HTML content; the platform's branded frame is applied around
     *             it before it is transmitted, so a caller sends content and not a document
     */
    public void send(String toEmail, String subject, String body, boolean html) {
        post("/v1/internal/mail/send",
                new SendMailRequest(toEmail, subject, body, null, html, false),
                "subject=" + subject, toEmail);
    }

    private void post(String uri, Object body, String what, String toEmail) {
        try {
            restClient.post()
                    .uri(uri)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<Object>>() {});
        } catch (RestClientException e) {
            log.warn("[MAIL] send failed ({}) to={}: {}", what, MaskingUtil.maskEmail(toEmail), e.getMessage());
        }
    }

    private record SendTemplateMailRequest(String templateKey, String toEmail, Map<String, String> tokens) {}

    private record SendMailRequest(String toEmail, String subject, String body, String fromAddress,
                                   boolean html, boolean arabic) {}
}
