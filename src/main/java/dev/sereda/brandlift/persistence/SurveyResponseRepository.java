package dev.sereda.brandlift.persistence;

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
