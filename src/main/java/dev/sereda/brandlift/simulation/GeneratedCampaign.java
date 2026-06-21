package dev.sereda.brandlift.simulation;

import java.time.Instant;

/**
 * A generated campaign. {@code campaignId} is derived deterministically from the
 * scenario so the same scenario always yields the same id. The measurement window
 * [{@code startedAt}, {@code endedAt}) is what "late" survey responses fall outside of.
 */
public record GeneratedCampaign(
        String campaignId,
        String name,
        String brand,
        Instant startedAt,
        Instant endedAt) {
}
