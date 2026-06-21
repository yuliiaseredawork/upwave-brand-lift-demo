package dev.sereda.brandlift.campaign;

import dev.sereda.brandlift.persistence.Campaign;
import java.time.Instant;
import java.util.UUID;

/**
 * API view of a campaign. Kept separate from the persistence {@link Campaign} record
 * so the wire shape and the storage shape can evolve independently.
 */
public record CampaignResponse(
        UUID id,
        String name,
        String brandName,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt,
        Instant updatedAt) {

    public static CampaignResponse from(Campaign campaign) {
        return new CampaignResponse(
                campaign.id(),
                campaign.name(),
                campaign.brandName(),
                campaign.startsAt(),
                campaign.endsAt(),
                campaign.createdAt(),
                campaign.updatedAt());
    }
}
