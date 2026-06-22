package dev.sereda.brandlift.lift;

import java.util.UUID;

/**
 * Thrown when a campaign cannot have lift calculated because one of the two groups
 * is empty. Lift is a comparison between exposed and control, so both must have at
 * least one response. Mapped to HTTP 422.
 */
public class InsufficientLiftDataException extends RuntimeException {

    public InsufficientLiftDataException(UUID campaignId, int exposedCount, int controlCount) {
        super("Cannot calculate lift for campaign " + campaignId
                + ": both exposed and control responses are required (exposed=" + exposedCount
                + ", control=" + controlCount + ")");
    }
}
