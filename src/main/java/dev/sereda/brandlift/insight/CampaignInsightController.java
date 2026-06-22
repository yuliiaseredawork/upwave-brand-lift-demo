package dev.sereda.brandlift.insight;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/campaigns/{id}/insights")
public class CampaignInsightController {

    private final CampaignInsightService service;

    public CampaignInsightController(CampaignInsightService service) {
        this.service = service;
    }

    @GetMapping
    public CampaignInsightResponse get(@PathVariable UUID id) {
        return service.generateForCampaign(id);
    }
}
