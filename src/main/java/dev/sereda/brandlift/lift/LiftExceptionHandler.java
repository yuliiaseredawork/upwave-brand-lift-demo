package dev.sereda.brandlift.lift;

import dev.sereda.brandlift.campaign.ApiError;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the lift-specific failures to HTTP responses, reusing the shared {@link ApiError}
 * body: not-yet-calculated summary -> 404, insufficient data -> 422. Campaign-not-found
 * is handled by the existing global advice.
 */
@RestControllerAdvice
public class LiftExceptionHandler {

    @ExceptionHandler(LiftSummaryNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleSummaryNotFound(LiftSummaryNotFoundException ex) {
        return new ApiError(ex.getMessage(), List.of());
    }

    @ExceptionHandler(InsufficientLiftDataException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiError handleInsufficientData(InsufficientLiftDataException ex) {
        return new ApiError(ex.getMessage(), List.of());
    }
}
