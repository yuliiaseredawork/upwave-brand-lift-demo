package dev.sereda.brandlift.persistence;

import java.time.Instant;
import java.util.UUID;

/**
 * A raw ad exposure row. {@code idempotencyKey} is unique across the table.
 * {@code receivedAt} is assigned by the database on insert. {@code creativeId} and
 * {@code placementId} are optional and may be {@code null}.
 */
public record AdExposureEvent(
        UUID id,
        UUID campaignId,
        String userIdHash,
        String channel,
        String creativeId,
        String placementId,
        Instant impressionTimestamp,
        String idempotencyKey,
        Instant receivedAt,
        boolean duplicate) {
}
