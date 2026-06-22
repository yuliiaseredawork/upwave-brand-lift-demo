package dev.sereda.brandlift.lift;

import java.util.UUID;

/**
 * Thrown when a campaign exists but no lift summary has been calculated for it yet.
 * Distinct from a missing campaign so the empty-state message is clear. Mapped to 404.
 */
public class LiftSummaryNotFoundException extends RuntimeException {

    public LiftSummaryNotFoundException(UUID campaignId) {
        super("No lift summary has been calculated for campaign: " + campaignId);
    }
}
