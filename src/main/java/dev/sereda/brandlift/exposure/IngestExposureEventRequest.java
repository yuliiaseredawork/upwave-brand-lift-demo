package dev.sereda.brandlift.exposure;

import dev.sereda.brandlift.simulation.Channel;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Request body for ingesting one ad exposure event. {@code creativeId} and
 * {@code placementId} are optional (the columns are nullable). {@code channel} is
 * validated against {@link Channel}, the single source of truth for the supported
 * set (the same set the database check constraint enforces).
 */
public record IngestExposureEventRequest(
        @NotNull UUID campaignId,
        @NotBlank String userIdHash,
        @NotBlank String channel,
        String creativeId,
        String placementId,
        @NotNull Instant impressionTimestamp,
        @NotBlank String idempotencyKey) {

    @AssertTrue(message = "channel must be one of the supported channels")
    public boolean isChannelSupported() {
        // Let @NotBlank report a missing channel; only check membership when present.
        if (channel == null || channel.isBlank()) {
            return true;
        }
        return Channel.isSupported(channel);
    }
}
