package dev.sereda.brandlift.survey;

import dev.sereda.brandlift.campaign.CampaignNotFoundException;
import dev.sereda.brandlift.persistence.CampaignRepository;
import dev.sereda.brandlift.persistence.SurveyResponse;
import dev.sereda.brandlift.persistence.SurveyResponseRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Ingestion of survey responses. Intentionally simpler than exposure ingestion:
 * survey responses have no natural idempotency key in this model, so each accepted
 * response is stored as its own raw record. The only rule beyond validation is that
 * the campaign must exist.
 */
@Service
public class SurveyResponseService {

    private final CampaignRepository campaignRepository;
    private final SurveyResponseRepository surveyResponseRepository;

    public SurveyResponseService(
            CampaignRepository campaignRepository,
            SurveyResponseRepository surveyResponseRepository) {
        this.campaignRepository = campaignRepository;
        this.surveyResponseRepository = surveyResponseRepository;
    }

    public SurveyResponse ingest(IngestSurveyResponseRequest request) {
        if (!campaignRepository.existsById(request.campaignId())) {
            throw new CampaignNotFoundException(request.campaignId());
        }

        UUID id = UUID.randomUUID();
        SurveyResponse response = new SurveyResponse(
                id,
                request.campaignId(),
                request.userIdHash(),
                request.exposed(),
                request.awarenessScore(),
                request.considerationScore(),
                request.purchaseIntentScore(),
                request.responseTimestamp(),
                null, // received_at assigned by the database
                request.lateOrDefault());
        surveyResponseRepository.insert(response);
        return surveyResponseRepository.findById(id).orElseThrow();
    }
}
