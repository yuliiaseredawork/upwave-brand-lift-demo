package dev.sereda.brandlift.survey;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Request body for ingesting one survey response.
 *
 * <p>Scores are boxed {@link Double} so {@code @NotNull} actually enforces
 * "required" — a primitive would silently default a missing field to 0. The 0..100
 * range is validated here for a clean 400; the database check constraints remain the
 * final guard. {@code late} is optional and treated as {@code false} when omitted.
 */
public record IngestSurveyResponseRequest(
        @NotNull UUID campaignId,
        @NotBlank String userIdHash,
        @NotNull Boolean exposed,
        @NotNull @DecimalMin("0") @DecimalMax("100") Double awarenessScore,
        @NotNull @DecimalMin("0") @DecimalMax("100") Double considerationScore,
        @NotNull @DecimalMin("0") @DecimalMax("100") Double purchaseIntentScore,
        @NotNull Instant responseTimestamp,
        Boolean late) {

    public boolean lateOrDefault() {
        return Boolean.TRUE.equals(late);
    }
}
