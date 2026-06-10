package com.sentinel.iot;

import com.sentinel.iot.config.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleIllegalArgument_returns400WithDetail() {
        ProblemDetail pd = handler.handleIllegalArgument(
                new IllegalArgumentException("Device name already exists: sensor-1"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getDetail()).isEqualTo("Device name already exists: sensor-1");
        assertThat(pd.getType().toString()).contains("bad-request");
    }

    @Test
    void handleNotFound_returns404WithDetail() {
        ProblemDetail pd = handler.handleNotFound(
                new NoSuchElementException("Device not found: abc"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(pd.getDetail()).isEqualTo("Device not found: abc");
        assertThat(pd.getType().toString()).contains("not-found");
    }

    @Test
    void handleAuthentication_returns401() {
        ProblemDetail pd = handler.handleAuthentication(
                new BadCredentialsException("Bad credentials"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(pd.getType().toString()).contains("unauthorized");
    }

    @Test
    void handleAccessDenied_returns403WithGenericMessage() {
        ProblemDetail pd = handler.handleAccessDenied(
                new AccessDeniedException("Forbidden"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(pd.getDetail()).isEqualTo("Access denied");
        assertThat(pd.getType().toString()).contains("forbidden");
    }

    @Test
    @SuppressWarnings("null")
    void handleValidation_returns400WithFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        org.springframework.validation.BindingResult bindingResult =
                mock(org.springframework.validation.BindingResult.class);
        org.springframework.validation.FieldError fieldError =
                new org.springframework.validation.FieldError("device", "name", "must not be blank");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of(fieldError));

        ProblemDetail pd = handler.handleValidation(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getDetail()).contains("name").contains("must not be blank");
        assertThat(pd.getType().toString()).contains("validation");
    }

    @Test
    void noProblemDetail_containsStackTrace() {
        ProblemDetail pd = handler.handleIllegalArgument(new IllegalArgumentException("test"));
        assertThat(pd.getProperties()).satisfiesAnyOf(
                props -> assertThat(props).isNull(),
                props -> assertThat(props).doesNotContainKey("stackTrace")
        );
    }
}
