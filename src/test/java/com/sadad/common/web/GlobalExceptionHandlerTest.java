package com.sadad.common.web;

import com.sadad.common.core.api.ApiError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * These four exceptions all describe a malformed request - the caller's mistake. Every one
 * of them used to fall through to the catch-all handler and be answered with 500 "an
 * unexpected error occurred", which told operators the service was broken when it was
 * behaving correctly, and hid genuine failures among routine bad requests. This handler is
 * shared by every service, so the regression would be platform-wide.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(
            // A real resolver over an empty catalogue: it falls back to ErrorCode's own
            // text, which is exactly the state a service is in before any refresh - and
            // therefore the state these assertions should be written against.
            new com.sadad.common.errors.ErrorMessageResolver(
                    new com.sadad.common.errors.ErrorCatalogCache(
                            java.time.Duration.ofMinutes(5), java.util.Map::of)));

    @Test
    @DisplayName("calling an endpoint with the wrong HTTP verb is 405, not 500")
    void wrongHttpMethodIsMethodNotAllowed() {
        ResponseEntity<ApiError> response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "POST")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(response.getBody().getError().getMessage()).contains("DELETE");
    }

    @Test
    @DisplayName("a non-JSON content type is 415, not 500")
    void wrongContentTypeIsUnsupportedMediaType() {
        ResponseEntity<ApiError> response = handler.handleMediaTypeNotSupported(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    @DisplayName("an unparseable body is 400, and the offending input is never echoed back")
    void malformedJsonIsBadRequestAndDoesNotLeakTheBody() {
        String body = "{\"password\":\"hunter2\"";
        ResponseEntity<ApiError> response = handler.handleUnreadableBody(
                new HttpMessageNotReadableException("JSON parse error: " + body,
                        new MockHttpInputMessage(body.getBytes(StandardCharsets.UTF_8))));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("MALFORMED_REQUEST_BODY");
        // The parser quotes the input it choked on, and on a login endpoint that is a credential.
        assertThat(response.getBody().getError().getMessage()).doesNotContain("hunter2");
    }

    @Test
    @DisplayName("a missing required parameter is 400 and names the parameter")
    void missingParameterIsBadRequest() {
        ResponseEntity<ApiError> response = handler.handleMissingParameter(
                new MissingServletRequestParameterException("status", "String"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        // Its own code now, so each language can place the parameter name itself.
        assertThat(response.getBody().getError().getCode()).isEqualTo("MISSING_PARAMETER");
        assertThat(response.getBody().getError().getMessage()).contains("status");
    }

    @Test
    @DisplayName("a path value of the wrong type is 400, naming the parameter but not its value")
    void typeMismatchIsBadRequestAndDoesNotLeakTheValue() {
        MethodArgumentTypeMismatchException ex =
                new MethodArgumentTypeMismatchException("secret-looking-value", java.util.UUID.class, "id", null, null);

        ResponseEntity<ApiError> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("PARAMETER_TYPE_MISMATCH");
        assertThat(response.getBody().getError().getMessage()).contains("id");
        assertThat(response.getBody().getError().getMessage()).doesNotContain("secret-looking-value");
    }

    @Test
    @DisplayName("an unknown path is still 404")
    void unknownPathIsNotFound() throws Exception {
        ResponseEntity<ApiError> response = handler.handleNoResourceFound(
                new org.springframework.web.servlet.resource.NoResourceFoundException(HttpMethod.GET, "/v1/admin/nope"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        // ROUTE_NOT_FOUND rather than RESOURCE_NOT_FOUND: "no such URL" and "no such
        // record" are different things to a caller, and they now say so.
        assertThat(response.getBody().getError().getCode()).isEqualTo("ROUTE_NOT_FOUND");
    }
}
