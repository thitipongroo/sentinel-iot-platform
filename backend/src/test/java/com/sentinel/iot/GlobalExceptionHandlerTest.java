package com.sentinel.iot;

import com.sentinel.iot.config.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ── 4xx client errors ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("4xx — client errors")
    class ClientErrors {

        @Test
        @DisplayName("IllegalArgumentException → 400 with problem detail and bad-request type")
        void handleIllegalArgument_returns400WithDetail() {
            ProblemDetail pd = handler.handleIllegalArgument(
                    new IllegalArgumentException("Device name already exists: sensor-1"));

            assertThat(pd.getStatus()).as("HTTP status").isEqualTo(HttpStatus.BAD_REQUEST.value());
            assertThat(pd.getDetail()).as("detail message").isEqualTo("Device name already exists: sensor-1");
            assertThat(pd.getType().toString()).as("type URI").contains("bad-request");
        }

        @Test
        @DisplayName("NoSuchElementException → 404 with problem detail and not-found type")
        void handleNotFound_returns404WithDetail() {
            ProblemDetail pd = handler.handleNotFound(
                    new NoSuchElementException("Device not found: abc"));

            assertThat(pd.getStatus()).as("HTTP status").isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(pd.getDetail()).as("detail message").isEqualTo("Device not found: abc");
            assertThat(pd.getType().toString()).as("type URI").contains("not-found");
        }

        @Test
        @DisplayName("BadCredentialsException → 401 with unauthorized type")
        void handleAuthentication_returns401() {
            ProblemDetail pd = handler.handleAuthentication(
                    new BadCredentialsException("Bad credentials"));

            assertThat(pd.getStatus()).as("HTTP status").isEqualTo(HttpStatus.UNAUTHORIZED.value());
            assertThat(pd.getType().toString()).as("type URI").contains("unauthorized");
        }

        @Test
        @DisplayName("AccessDeniedException → 403 with generic 'Access denied' message")
        void handleAccessDenied_returns403WithGenericMessage() {
            ProblemDetail pd = handler.handleAccessDenied(
                    new AccessDeniedException("Forbidden"));

            assertThat(pd.getStatus()).as("HTTP status").isEqualTo(HttpStatus.FORBIDDEN.value());
            assertThat(pd.getDetail()).as("detail must be generic, not the raw exception message")
                    .isEqualTo("Access denied");
            assertThat(pd.getType().toString()).as("type URI").contains("forbidden");
        }

        @Test
        @SuppressWarnings("null")
        @DisplayName("MethodArgumentNotValidException → 400 with field name and constraint message")
        void handleValidation_returns400WithFieldErrors() {
            MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
            BindingResult bindingResult = mock(BindingResult.class);
            FieldError fieldError = new FieldError("device", "name", "must not be blank");
            when(ex.getBindingResult()).thenReturn(bindingResult);
            when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

            ProblemDetail pd = handler.handleValidation(ex);

            assertThat(pd.getStatus()).as("HTTP status").isEqualTo(HttpStatus.BAD_REQUEST.value());
            assertThat(pd.getDetail()).as("detail must mention field and violation")
                    .contains("name").contains("must not be blank");
            assertThat(pd.getType().toString()).as("type URI").contains("validation");
        }
    }

    // ── Security invariant: no stack traces exposed ───────────────────────────

    @Nested
    @DisplayName("Security invariant")
    class SecurityInvariant {

        @Test
        @DisplayName("ProblemDetail never leaks a stackTrace property to clients")
        void problemDetail_neverContainsStackTrace() {
            ProblemDetail pd = handler.handleIllegalArgument(new IllegalArgumentException("test"));

            assertThat(pd.getProperties()).satisfiesAnyOf(
                    props -> assertThat(props).isNull(),
                    props -> assertThat(props).doesNotContainKey("stackTrace"));
        }
    }
}
