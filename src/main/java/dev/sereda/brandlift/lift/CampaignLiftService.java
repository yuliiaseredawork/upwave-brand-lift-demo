package dev.sereda.brandlift.lift;

import dev.sereda.brandlift.campaign.CampaignNotFoundException;
import dev.sereda.brandlift.persistence.CampaignLiftSummary;
import dev.sereda.brandlift.persistence.CampaignLiftSummaryRepository;
import dev.sereda.brandlift.persistence.CampaignRepository;
import dev.sereda.brandlift.persistence.SurveyResponseRepository;
import dev.sereda.brandlift.persistence.SurveyResponseRepository.SurveyAggregate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Computes and serves campaign lift summaries.
 *
 * <p>Lift is intentionally simple: for each metric, exposed-group average minus
 * control-group average, in score points. Raw survey responses are the source of
 * truth; the summary is recomputed from them and persisted so reads are cheap.
 * No statistical significance or causal inference is attempted (see design notes).
 */
@Service
public class CampaignLiftService {

    private final CampaignRepository campaignRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final CampaignLiftSummaryRepository liftSummaryRepository;

    public CampaignLiftService(
            CampaignRepository campaignRepository,
            SurveyResponseRepository surveyResponseRepository,
            CampaignLiftSummaryRepository liftSummaryRepository) {
        this.campaignRepository = campaignRepository;
        this.surveyResponseRepository = surveyResponseRepository;
        this.liftSummaryRepository = liftSummaryRepository;
    }

    public CampaignLiftSummary recalculate(UUID campaignId) {
        requireCampaign(campaignId);

        SurveyAggregate aggregate = surveyResponseRepository.aggregateByCampaign(campaignId);
        if (aggregate.exposedCount() == 0 || aggregate.controlCount() == 0) {
            throw new InsufficientLiftDataException(
                    campaignId, aggregate.exposedCount(), aggregate.controlCount());
        }

        liftSummaryRepository.upsert(toSummary(campaignId, aggregate));
        // Read back so the response carries the database-assigned calculated_at.
        return liftSummaryRepository.findByCampaignId(campaignId).orElseThrow();
    }

    public CampaignLiftSummary getSummary(UUID campaignId) {
        requireCampaign(campaignId);
        return liftSummaryRepository.findByCampaignId(campaignId)
                .orElseThrow(() -> new LiftSummaryNotFoundException(campaignId));
    }

    private void requireCampaign(UUID campaignId) {
        if (!campaignRepository.existsById(campaignId)) {
            throw new CampaignNotFoundException(campaignId);
        }
    }

    /**
     * Build the summary from the aggregate. Averages are rounded to 2 decimals and
     * lift is computed from the rounded averages, so the persisted numbers are
     * internally consistent (exposed - control == lift to the displayed precision).
     */
    private static CampaignLiftSummary toSummary(UUID campaignId, SurveyAggregate aggregate) {
        double exposedAwareness = round2(aggregate.exposedAvgAwareness());
        double controlAwareness = round2(aggregate.controlAvgAwareness());
        double exposedConsideration = round2(aggregate.exposedAvgConsideration());
        double controlConsideration = round2(aggregate.controlAvgConsideration());
        double exposedPurchaseIntent = round2(aggregate.exposedAvgPurchaseIntent());
        double controlPurchaseIntent = round2(aggregate.controlAvgPurchaseIntent());

        return new CampaignLiftSummary(
                campaignId,
                aggregate.exposedCount(),
                aggregate.controlCount(),
                exposedAwareness,
                controlAwareness,
                round2(exposedAwareness - controlAwareness),
                exposedConsideration,
                controlConsideration,
                round2(exposedConsideration - controlConsideration),
                exposedPurchaseIntent,
                controlPurchaseIntent,
                round2(exposedPurchaseIntent - controlPurchaseIntent),
                null); // calculated_at assigned by the database on upsert
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
