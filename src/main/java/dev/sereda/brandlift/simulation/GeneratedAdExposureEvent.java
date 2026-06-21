package dev.sereda.brandlift.simulation;

import java.time.Instant;

/**
 * A single ad exposure: a user saw a campaign on a channel at a point in time.
 *
 * <p>{@code idempotencyKey} is stable per logical exposure. A duplicate (modeling
 * at-least-once redelivery) is an event with the <em>same</em> idempotency key and
 * identical fields, which is exactly what ingestion will later dedup on.
 */
public record GeneratedAdExposureEvent(
        String idempotencyKey,
        String campaignId,
        String userId,
        Channel channel,
        Instant exposedAt) {
}
