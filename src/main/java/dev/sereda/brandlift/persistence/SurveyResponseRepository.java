package dev.sereda.brandlift.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Explicit JDBC persistence for raw survey responses. Out-of-range scores violate
 * the schema check constraints and surface as a
 * {@link org.springframework.dao.DataIntegrityViolationException}.
 */
@Repository
public class SurveyResponseRepository {

    private final JdbcClient jdbcClient;

    public SurveyResponseRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(SurveyResponse response) {
        jdbcClient.sql("""
                insert into survey_responses
                    (id, campaign_id, user_id_hash, exposed, awareness_score,
                     consideration_score, purchase_intent_score, response_timestamp, is_late)
                values
                    (:id, :campaignId, :userIdHash, :exposed, :awarenessScore,
                     :considerationScore, :purchaseIntentScore, :responseTimestamp, :late)
                """)
                .param("id", response.id())
                .param("campaignId", response.campaignId())
                .param("userIdHash", response.userIdHash())
                .param("exposed", response.exposed())
                .param("awarenessScore", response.awarenessScore())
                .param("considerationScore", response.considerationScore())
                .param("purchaseIntentScore", response.purchaseIntentScore())
                .param("responseTimestamp", response.responseTimestamp().atOffset(ZoneOffset.UTC))
                .param("late", response.late())
                .update();
    }

    public Optional<SurveyResponse> findById(UUID id) {
        return jdbcClient.sql("select * from survey_responses where id = :id")
                .param("id", id)
                .query(SurveyResponseRepository::mapRow)
                .optional();
    }

    /**
     * Per-group counts and average scores for a campaign, computed in one pass with
     * Postgres FILTER. Always returns exactly one row; a group's averages are
     * {@code null} when that group has no responses (so the caller can detect
     * insufficient data before computing lift).
     */
    public SurveyAggregate aggregateByCampaign(UUID campaignId) {
        return jdbcClient.sql("""
                select
                    count(*) filter (where exposed)                        as exposed_count,
                    count(*) filter (where not exposed)                    as control_count,
                    avg(awareness_score) filter (where exposed)            as exposed_avg_awareness,
                    avg(awareness_score) filter (where not exposed)        as control_avg_awareness,
                    avg(consideration_score) filter (where exposed)        as exposed_avg_consideration,
                    avg(consideration_score) filter (where not exposed)    as control_avg_consideration,
                    avg(purchase_intent_score) filter (where exposed)      as exposed_avg_purchase_intent,
                    avg(purchase_intent_score) filter (where not exposed)  as control_avg_purchase_intent
                from survey_responses
                where campaign_id = :campaignId
                """)
                .param("campaignId", campaignId)
                .query(SurveyResponseRepository::mapAggregate)
                .single();
    }

    /**
     * Aggregate survey stats for one campaign. Averages are nullable because a group
     * with no responses produces a SQL NULL average.
     */
    public record SurveyAggregate(
            int exposedCount,
            int controlCount,
            Double exposedAvgAwareness,
            Double controlAvgAwareness,
            Double exposedAvgConsideration,
            Double controlAvgConsideration,
            Double exposedAvgPurchaseIntent,
            Double controlAvgPurchaseIntent) {
    }

    private static SurveyAggregate mapAggregate(ResultSet rs, int rowNum) throws SQLException {
        return new SurveyAggregate(
                rs.getInt("exposed_count"),
                rs.getInt("control_count"),
                nullableDouble(rs, "exposed_avg_awareness"),
                nullableDouble(rs, "control_avg_awareness"),
                nullableDouble(rs, "exposed_avg_consideration"),
                nullableDouble(rs, "control_avg_consideration"),
                nullableDouble(rs, "exposed_avg_purchase_intent"),
                nullableDouble(rs, "control_avg_purchase_intent"));
    }

    /** avg() returns numeric; read it as BigDecimal so SQL NULL maps cleanly to a null Double. */
    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : value.doubleValue();
    }

    private static SurveyResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new SurveyResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("campaign_id", UUID.class),
                rs.getString("user_id_hash"),
                rs.getBoolean("exposed"),
                rs.getDouble("awareness_score"),
                rs.getDouble("consideration_score"),
                rs.getDouble("purchase_intent_score"),
                rs.getObject("response_timestamp", OffsetDateTime.class).toInstant(),
                rs.getObject("received_at", OffsetDateTime.class).toInstant(),
                rs.getBoolean("is_late"));
    }
}
