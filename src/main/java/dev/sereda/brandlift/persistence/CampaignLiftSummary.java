package dev.sereda.brandlift.persistence;

import java.time.Instant;
import java.util.UUID;

/**
 * A computed campaign lift summary (one row per campaign). Fully derivable from the
 * raw survey responses; persisted so reads are cheap and the result is stable until
 * the next recalculation. {@code calculatedAt} is assigned by the database on upsert,
 * so it is {@code null} on a value being written and populated on a value read back.
 */
public record CampaignLiftSummary(
        UUID campaignId,
        int exposedUserCount,
        int controlUserCount,
        double exposedAvgAwareness,
        double controlAvgAwareness,
        double awarenessLift,
        double exposedAvgConsideration,
        double controlAvgConsideration,
        double considerationLift,
        double exposedAvgPurchaseIntent,
        double controlAvgPurchaseIntent,
        double purchaseIntentLift,
        Instant calculatedAt) {
}
