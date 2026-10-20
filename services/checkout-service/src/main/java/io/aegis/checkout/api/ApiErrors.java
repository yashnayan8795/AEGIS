package io.aegis.checkout.api;

import io.aegis.checkout.application.*;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiErrors extends ResponseEntityExceptionHandler {
    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalid(HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request does not match the API contract.", request);
    }
    @ExceptionHandler(PurchaseNotFoundException.class)
    ProblemDetail missing(HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "PURCHASE_NOT_FOUND", "Resource not found; absence does not establish a failed operation.", request);
    }
    @ExceptionHandler({DataAccessResourceFailureException.class, QueryTimeoutException.class})
    ProblemDetail unavailable(HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "Storage is temporarily unavailable; a previous mutation outcome may be unknown.", request);
    }
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.", request);
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
