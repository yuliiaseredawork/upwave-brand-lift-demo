package dev.sereda.brandlift.campaign;

import java.util.UUID;

/** Thrown when a campaign lookup fails. Mapped to HTTP 404 by the exception handler. */
public class CampaignNotFoundException extends RuntimeException {

    public CampaignNotFoundException(UUID id) {
        super("Campaign not found: " + id);
    }
}
