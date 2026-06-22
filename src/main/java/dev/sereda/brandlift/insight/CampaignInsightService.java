package dev.sereda.brandlift.insight;

import dev.sereda.brandlift.campaign.CampaignNotFoundException;
import dev.sereda.brandlift.lift.LiftSummaryNotFoundException;
import dev.sereda.brandlift.persistence.CampaignLiftSummary;
import dev.sereda.brandlift.persistence.CampaignLiftSummaryRepository;
import dev.sereda.brandlift.persistence.CampaignRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Serves campaign insights. This reads the already-persisted lift summary and asks the
 * generator to describe it — it never recalculates lift, so insight generation cannot
 * change stored numbers and stays cheap and side-effect free.
 */
@Service
public class CampaignInsightService {

    private final CampaignRepository campaignRepository;
    private final CampaignLiftSummaryRepository liftSummaryRepository;
    private final CampaignInsightGenerator insightGenerator;

    public CampaignInsightService(
            CampaignRepository campaignRepository,
            CampaignLiftSummaryRepository liftSummaryRepository,
            CampaignInsightGenerator insightGenerator) {
        this.campaignRepository = campaignRepository;
        this.liftSummaryRepository = liftSummaryRepository;
        this.insightGenerator = insightGenerator;
    }

    public CampaignInsightResponse generateForCampaign(UUID campaignId) {
        if (!campaignRepository.existsById(campaignId)) {
            throw new CampaignNotFoundException(campaignId);
        }

        CampaignLiftSummary summary = liftSummaryRepository.findByCampaignId(campaignId)
                .orElseThrow(() -> new LiftSummaryNotFoundException(campaignId));

        CampaignInsight insight = insightGenerator.generate(summary);
        return CampaignInsightResponse.from(campaignId, Instant.now(), insight);
    }
}
