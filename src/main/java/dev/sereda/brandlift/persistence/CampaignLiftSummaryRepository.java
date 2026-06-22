package dev.sereda.brandlift.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Explicit JDBC persistence for the computed lift summary. One row per campaign, so
 * {@link #upsert(CampaignLiftSummary)} inserts-or-updates keyed on campaign_id and
 * refreshes calculated_at to now() on every write.
 */
@Repository
public class CampaignLiftSummaryRepository {

    private final JdbcClient jdbcClient;

    public CampaignLiftSummaryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<CampaignLiftSummary> findByCampaignId(UUID campaignId) {
        return jdbcClient.sql("select * from campaign_lift_summaries where campaign_id = :id")
                .param("id", campaignId)
                .query(CampaignLiftSummaryRepository::mapRow)
                .optional();
    }

    public void upsert(CampaignLiftSummary summary) {
        jdbcClient.sql("""
                insert into campaign_lift_summaries (
                    campaign_id, exposed_user_count, control_user_count,
                    exposed_avg_awareness, control_avg_awareness, awareness_lift,
                    exposed_avg_consideration, control_avg_consideration, consideration_lift,
                    exposed_avg_purchase_intent, control_avg_purchase_intent, purchase_intent_lift,
                    calculated_at)
                values (
                    :campaignId, :exposedUserCount, :controlUserCount,
                    :exposedAvgAwareness, :controlAvgAwareness, :awarenessLift,
                    :exposedAvgConsideration, :controlAvgConsideration, :considerationLift,
                    :exposedAvgPurchaseIntent, :controlAvgPurchaseIntent, :purchaseIntentLift,
                    now())
                on conflict (campaign_id) do update set
                    exposed_user_count          = excluded.exposed_user_count,
                    control_user_count          = excluded.control_user_count,
                    exposed_avg_awareness       = excluded.exposed_avg_awareness,
                    control_avg_awareness       = excluded.control_avg_awareness,
                    awareness_lift              = excluded.awareness_lift,
                    exposed_avg_consideration   = excluded.exposed_avg_consideration,
                    control_avg_consideration   = excluded.control_avg_consideration,
                    consideration_lift          = excluded.consideration_lift,
                    exposed_avg_purchase_intent = excluded.exposed_avg_purchase_intent,
                    control_avg_purchase_intent = excluded.control_avg_purchase_intent,
                    purchase_intent_lift        = excluded.purchase_intent_lift,
                    calculated_at               = now()
                """)
                .param("campaignId", summary.campaignId())
                .param("exposedUserCount", summary.exposedUserCount())
                .param("controlUserCount", summary.controlUserCount())
                .param("exposedAvgAwareness", summary.exposedAvgAwareness())
                .param("controlAvgAwareness", summary.controlAvgAwareness())
                .param("awarenessLift", summary.awarenessLift())
                .param("exposedAvgConsideration", summary.exposedAvgConsideration())
                .param("controlAvgConsideration", summary.controlAvgConsideration())
                .param("considerationLift", summary.considerationLift())
                .param("exposedAvgPurchaseIntent", summary.exposedAvgPurchaseIntent())
                .param("controlAvgPurchaseIntent", summary.controlAvgPurchaseIntent())
                .param("purchaseIntentLift", summary.purchaseIntentLift())
                .update();
    }

    private static CampaignLiftSummary mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new CampaignLiftSummary(
                rs.getObject("campaign_id", UUID.class),
                rs.getInt("exposed_user_count"),
                rs.getInt("control_user_count"),
                rs.getDouble("exposed_avg_awareness"),
                rs.getDouble("control_avg_awareness"),
                rs.getDouble("awareness_lift"),
                rs.getDouble("exposed_avg_consideration"),
                rs.getDouble("control_avg_consideration"),
                rs.getDouble("consideration_lift"),
                rs.getDouble("exposed_avg_purchase_intent"),
                rs.getDouble("control_avg_purchase_intent"),
                rs.getDouble("purchase_intent_lift"),
                rs.getObject("calculated_at", OffsetDateTime.class).toInstant());
    }
}
