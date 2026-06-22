package dev.sereda.brandlift.lift;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/campaigns/{id}/lift-summary")
public class CampaignLiftController {

    private final CampaignLiftService service;

    public CampaignLiftController(CampaignLiftService service) {
        this.service = service;
    }

    @PostMapping("/recalculate")
    public CampaignLiftSummaryResponse recalculate(@PathVariable UUID id) {
        return CampaignLiftSummaryResponse.from(service.recalculate(id));
    }

    @GetMapping
    public CampaignLiftSummaryResponse get(@PathVariable UUID id) {
        return CampaignLiftSummaryResponse.from(service.getSummary(id));
    }
}
