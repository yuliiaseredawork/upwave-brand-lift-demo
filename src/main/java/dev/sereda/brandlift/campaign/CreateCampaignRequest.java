package dev.sereda.brandlift.campaign;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Request body for creating a campaign. Validation is declared here so the
 * controller stays thin; the cross-field rule (window must be valid) is an
 * {@link AssertTrue} method, which is the simplest readable way to express it.
 */
public record CreateCampaignRequest(
        @NotBlank String name,
        @NotBlank String brandName,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt) {

    @AssertTrue(message = "endsAt must be after startsAt")
    public boolean isEndsAtAfterStartsAt() {
        // Let @NotNull report missing values; only check ordering when both are present.
        if (startsAt == null || endsAt == null) {
            return true;
        }
        return endsAt.isAfter(startsAt);
    }
}
