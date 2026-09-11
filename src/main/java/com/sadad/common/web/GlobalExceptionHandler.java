package com.sadad.common.web;

import com.sadad.common.core.api.ApiError;
import com.sadad.common.core.context.RequestContext;
import com.sadad.common.exception.PlatformException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import com.sadad.common.errors.ErrorCode;
import com.sadad.common.errors.ErrorMessageResolver;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ErrorMessageResolver messages;

    /**
     * The sentence this particular caller should read, for a code with no runtime values.
     *
     * <p>Everything user-facing in this class goes through here, so the language a request
     * asked for is honoured whether the failure came from business logic, from Bean
     * Validation, or from Spring's own machinery. Before this, a customer reading the portal
     * in Arabic got Arabic until something went wrong, and English at exactly the moment
     * they most needed to understand.
     */
    private String say(ErrorCode code) {
        return say(code, java.util.Map.of());
    }


    /**
     * Renders a Bean Validation message, translating it when the annotation named a code.
     *
     * <p>A constraint's {@code message} is a compiled-in string, so a field rejected by
     * {@code @Pattern} produced English under every language - an Arabic form showing an
     * Arabic headline above an English field error. Annotations may now name an
     * {@link ErrorCode} instead, and this renders it for the reader. Anything that is not a
     * known code passes through unchanged, so existing constraints keep working.
     */
    /**
     * Field names whose value must never be echoed back to the caller.
     *
     * <p>A rejected value is genuinely useful for a mistyped IBAN or a malformed date, which
     * is why it is returned at all. It is the opposite of useful for a secret: a credential
     * that fails validation was still a real credential, and echoing it puts it in the
     * response body, the browser's network tab, any proxy log along the way and any error
     * monitor that captures failed requests. Matched on the field name because that is the
     * only signal available here - a fail-closed list, so a new field called "apiSecret" is
     * covered the day it is added rather than the day somebody notices.
     */
    private static final java.util.regex.Pattern SENSITIVE_FIELD = java.util.regex.Pattern.compile(
            "(?i).*(password|passcode|credential|secret|token|otp|pin|apikey|api_key|"
                    + "subscriptionkey|privatekey|authorization|signature|"
                    // Card fields. A validation failure on any of these must not echo what
                    // was submitted: the value would land in the response body, the browser's
                    // network tab, every proxy log on the way and any error monitor watching
                    // failed requests - which is precisely how cardholder data escapes a
                    // system that was never meant to hold it.
                    //
                    // `iban` is deliberately NOT here. An IBAN is echoed back on purpose -
                    // "you typed SA03 8000..." is what makes a validation message actionable,
                    // and it is the reason rejectedValue is returned at all. Nor is
                    // `lastFour`: the field is meant to hold four digits, and a full card
                    // number submitted into it is caught by its shape below rather than by
                    // suppressing a field that is legitimately displayed.
                    + "cardnumber|card_number|cardno|pan|cvc|cvv|securitycode).*");

    /**
     * A rejected value that looks like it could be a card number, whatever the field is called.
     *
     * <p>The field-name list above is fail-closed but not clairvoyant: a PAN submitted into a
     * field nobody thought of is still a PAN. Thirteen to nineteen digits, allowing the spaces
     * and dashes people type, is the shape of every card scheme in use. Matching on the value
     * as well as the name is deliberate belt-and-braces - the cost of redacting a long number
     * that happened not to be a card is nil, and the cost of echoing one that was is not.
     */
    private static final java.util.regex.Pattern LOOKS_LIKE_A_CARD_NUMBER =
            java.util.regex.Pattern.compile("[0-9][0-9 -]{11,22}[0-9]");

    /** The rejected value, or a marker when the field is one whose value must not travel. */
    private Object safeRejectedValue(String field, Object rejectedValue) {
        if (rejectedValue == null) return null;
        if (field != null && SENSITIVE_FIELD.matcher(field).matches()) return "[redacted]";
        if (rejectedValue instanceof CharSequence candidate
                && LOOKS_LIKE_A_CARD_NUMBER.matcher(candidate).matches()) {
            return "[redacted]";
        }
        // Even a non-sensitive value has no business being echoed at length: a rejected
        // 5 MB string would be returned in full, to no one's benefit.
        if (rejectedValue instanceof CharSequence text && text.length() > 200) {
            return text.subSequence(0, 200) + "… (" + text.length() + " characters)";
        }
        return rejectedValue;
    }

    private String fieldMessage(String raw) {
        if (raw == null) return null;
        return ErrorCode.find(raw)
                .map(this::say)
                .orElse(raw);
    }

    private String say(ErrorCode code, java.util.Map<String, Object> params) {
        return messages.resolve(code.code(), RequestContext.currentLocale(), params, code.getDefaultMessageEn());
    }

    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<ApiError> handlePlatformException(PlatformException ex) {
        String requestId = RequestContext.currentRequestId();
        log.warn("Business or Platform exception [{}]: {} (requestId={})", ex.getCode(), ex.getMessage(), requestId);

        // The catalogue decides the wording; ex.getMessage() is the developer's version and
        // stays in the log line above, where it is useful, rather than going to the customer.
        String message = messages.resolve(
                ex.getCode(), RequestContext.currentLocale(), ex.getParams(), ex.getMessage());
        ApiError error = ApiError.of(ex.getCode(), message, requestId);
        return ResponseEntity.status(ex.getHttpStatus()).body(error);
    }

    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    public ResponseEntity<ApiError> handleAccessDenied(RuntimeException ex) {
        String requestId = RequestContext.currentRequestId();
        log.warn("Access denied (requestId={}): {}", requestId, ex.getMessage());

        ApiError error = ApiError.of(ErrorCode.ACCESS_DENIED.code(), say(ErrorCode.ACCESS_DENIED), requestId);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex) {
        String requestId = RequestContext.currentRequestId();
        List<ApiError.FieldError> details = new ArrayList<>();

        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            details.add(ApiError.FieldError.builder()
                    .field(fe.getField())
                    .message(fieldMessage(fe.getDefaultMessage()))
                    .rejectedValue(safeRejectedValue(fe.getField(), fe.getRejectedValue()))
                    .build());
        }

        log.warn("Validation error on request {}: {} field errors", requestId, details.size());
        ApiError error = ApiError.of(ErrorCode.VALIDATION_ERROR.code(), say(ErrorCode.VALIDATION_ERROR), requestId, details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        String requestId = RequestContext.currentRequestId();
        log.warn("Upload rejected - file too large (requestId={})", requestId);
        ApiError error = ApiError.of(ErrorCode.FILE_TOO_LARGE.code(), say(ErrorCode.FILE_TOO_LARGE, java.util.Map.of("limit", "5 MB")), requestId);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
    }

    /**
     * Constraint violations on {@code @RequestParam} / {@code @PathVariable} arguments of a
     * {@code @Validated} controller. Spring reports these differently from a rejected
     * {@code @RequestBody} - as a {@link HandlerMethodValidationException} (or, on the
     * programmatic path, a {@link ConstraintViolationException}) rather than a
     * {@code MethodArgumentNotValidException} - so without this pair they fell through to the
     * catch-all below and a plainly bad query parameter was reported to the caller as an
     * internal server error.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex) {
        List<String> messages = ex.getAllValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .toList();
        return badRequest(messages);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> messages = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .toList();
        return badRequest(messages);
    }

    /** One shape for both of the above, reusing the same {@code details} structure a rejected
     * request body already produces - a caller should not have to parse two different error
     * formats depending on where the bad value happened to arrive. */
    private ResponseEntity<ApiError> badRequest(List<String> messages) {
        List<ApiError.FieldError> details = messages.stream()
                .map(message -> ApiError.FieldError.builder().message(message).build())
                .toList();
        log.debug("[VALIDATION] Rejected request: {}", messages);
        return ResponseEntity.badRequest().body(ApiError.of(
                ErrorCode.VALIDATION_ERROR.code(),
                // The individual violations are already in `details`; the headline is the
                // catalogue's translated sentence rather than one untranslated violation.
                say(ErrorCode.VALIDATION_ERROR),
                RequestContext.currentRequestId(),
                details));
    }

    /**
     * An unknown path is a 404, not a 500. Without this it fell through to the catch-all
     * below and every mistyped URL - or every call to an endpoint that has since been
     * removed - was reported to the caller as "an unexpected error occurred" and logged at
     * ERROR. That is actively misleading: it tells an operator the service is broken when
     * nothing is wrong with it, and it buries real failures in error dashboards. Logged at
     * DEBUG for the same reason: a 404 on a public API is routine traffic, not an incident.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("[NOT-FOUND] No handler for {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(
                ErrorCode.ROUTE_NOT_FOUND.code(),
                say(ErrorCode.ROUTE_NOT_FOUND),
                RequestContext.currentRequestId()));
    }

    /**
     * A request the service understood but cannot accept is the caller's mistake, not a
     * server fault. Each of these used to fall through to the catch-all below and be
     * answered with 500 "an unexpected error occurred" and logged at ERROR - which tells an
     * operator the service is broken when it is working exactly as designed, and buries real
     * incidents under routine bad requests. They are logged at DEBUG for the same reason.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.debug("[METHOD-NOT-ALLOWED] {} is not supported here", ex.getMethod());
        String supported = ex.getSupportedHttpMethods() == null ? "" : ex.getSupportedHttpMethods().toString();
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiError.of(
                ErrorCode.METHOD_NOT_ALLOWED.code(),
                say(ErrorCode.METHOD_NOT_ALLOWED, java.util.Map.of(
                        "method", String.valueOf(ex.getMethod()),
                        "supported", supported.isBlank() ? "-" : supported)),
                RequestContext.currentRequestId()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.debug("[UNSUPPORTED-MEDIA-TYPE] {}", ex.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(ApiError.of(
                ErrorCode.UNSUPPORTED_MEDIA_TYPE.code(),
                say(ErrorCode.UNSUPPORTED_MEDIA_TYPE),
                RequestContext.currentRequestId()));
    }

    /**
     * A body that is not parseable JSON. The parser's own message is deliberately not echoed
     * back: it quotes the offending input, which on these endpoints can be a credential.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("[BAD-BODY] Unreadable request body: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.badRequest().body(ApiError.of(
                ErrorCode.MALFORMED_REQUEST_BODY.code(),
                say(ErrorCode.MALFORMED_REQUEST_BODY),
                RequestContext.currentRequestId()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex) {
        // Its own code, so the parameter name can be placed by each translation rather than
        // welded to English word order.
        return ResponseEntity.badRequest().body(ApiError.of(
                ErrorCode.MISSING_PARAMETER.code(),
                say(ErrorCode.MISSING_PARAMETER, java.util.Map.of("parameter", ex.getParameterName())),
                RequestContext.currentRequestId()));
    }

    /**
     * A path or query value of the wrong shape - a malformed UUID, a non-numeric page. The
     * rejected value is not echoed back for the same reason as above.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(ApiError.of(
                ErrorCode.PARAMETER_TYPE_MISMATCH.code(),
                say(ErrorCode.PARAMETER_TYPE_MISMATCH, java.util.Map.of("parameter", ex.getName())),
                RequestContext.currentRequestId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception ex) {
        String requestId = RequestContext.currentRequestId();
        log.error("Unhandled internal system error (requestId={})", requestId, ex);

        // Safe message without exposing internal stack trace or SQL
        // Translated too. This is the message a customer sees when something we did not
        // anticipate goes wrong, so it is the last place that should switch to English.
        ApiError error = ApiError.of(ErrorCode.INTERNAL_SERVER_ERROR.code(),
                say(ErrorCode.INTERNAL_SERVER_ERROR), requestId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
