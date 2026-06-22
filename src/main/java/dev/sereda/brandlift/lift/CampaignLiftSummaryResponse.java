package dev.sereda.brandlift.lift;

import dev.sereda.brandlift.persistence.CampaignLiftSummary;
import java.time.Instant;
import java.util.UUID;

/** API view of a campaign lift summary. */
public record CampaignLiftSummaryResponse(
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

    public static CampaignLiftSummaryResponse from(CampaignLiftSummary summary) {
        return new CampaignLiftSummaryResponse(
                summary.campaignId(),
                summary.exposedUserCount(),
                summary.controlUserCount(),
                summary.exposedAvgAwareness(),
                summary.controlAvgAwareness(),
                summary.awarenessLift(),
                summary.exposedAvgConsideration(),
                summary.controlAvgConsideration(),
                summary.considerationLift(),
                summary.exposedAvgPurchaseIntent(),
                summary.controlAvgPurchaseIntent(),
                summary.purchaseIntentLift(),
                summary.calculatedAt());
    }
}
