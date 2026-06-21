package dev.sereda.brandlift.campaign;

import dev.sereda.brandlift.persistence.Campaign;
import dev.sereda.brandlift.persistence.CampaignRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Campaign use cases. Thin on purpose: it owns id generation and the not-found
 * rule, and delegates storage to the repository. After insert it reads the row
 * back so the response carries the database-assigned created_at / updated_at.
 */
@Service
public class CampaignService {

    private final CampaignRepository repository;

    public CampaignService(CampaignRepository repository) {
        this.repository = repository;
    }

    public Campaign create(CreateCampaignRequest request) {
        UUID id = UUID.randomUUID();
        Campaign campaign = new Campaign(
                id,
                request.name(),
                request.brandName(),
                request.startsAt(),
                request.endsAt(),
                null,  // created_at assigned by the database
                null); // updated_at assigned by the database
        repository.insert(campaign);
        return repository.findById(id).orElseThrow(() -> new CampaignNotFoundException(id));
    }

    public Campaign getById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new CampaignNotFoundException(id));
    }

    public List<Campaign> list() {
        return repository.findAll();
    }
}
