package io.aegis.payment.api;

import io.aegis.payment.application.*;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiErrors extends ResponseEntityExceptionHandler {
    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalid(HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request does not match the API contract.", request);
    }
    @ExceptionHandler(PaymentNotFoundException.class)
    ProblemDetail missing(HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Resource not found; absence does not establish a failed operation.", request);
    }
    @ExceptionHandler({DataAccessResourceFailureException.class, QueryTimeoutException.class})
    ProblemDetail unavailable(HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "Storage is temporarily unavailable; a previous mutation outcome may be unknown.", request);
    }
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.", request);
    }
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex, problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Request does not match the API contract.", ((org.springframework.web.context.request.ServletWebRequest) request).getRequest()), headers, status, request);
    }
    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex, problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Request media type must be application/json.", ((org.springframework.web.context.request.ServletWebRequest) request).getRequest()), headers, status, request);
    }
    private ProblemDetail problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
        var value = ProblemDetail.forStatusAndDetail(status, detail);
        value.setType(URI.create("about:blank"));
        value.setTitle(status.getReasonPhrase());
        value.setInstance(URI.create(request.getRequestURI()));
        value.setProperty("code", code);
        return value;
    }
}
