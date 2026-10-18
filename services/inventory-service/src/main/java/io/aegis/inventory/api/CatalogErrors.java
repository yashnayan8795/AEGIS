package io.aegis.inventory.api;

import io.aegis.inventory.application.InvalidSkuException;
import io.aegis.inventory.application.ItemNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CatalogController.class)
public class CatalogErrors {
    @ExceptionHandler(InvalidSkuException.class)
    ProblemDetail invalid(HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "SKU must contain 1 to 64 uppercase letters, digits, underscores or hyphens, starting with a letter or digit.", request);
    }
    @ExceptionHandler(ItemNotFoundException.class)
    ProblemDetail missing(HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Catalog item not found.", request);
    }
    @ExceptionHandler({DataAccessResourceFailureException.class, QueryTimeoutException.class})
    ProblemDetail unavailable(HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "Catalog storage is temporarily unavailable.", request);
    }
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.", request);
    }
    private ProblemDetail problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return problem;
    }
}
