package dev.sereda.brandlift.campaign;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack API tests: HTTP -> controller -> service -> repository -> real Postgres
 * (Testcontainers). Covers the happy paths plus the validation and not-found responses.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CampaignApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String VALID_BODY = """
            {
              "name": "Spring Awareness Push",
              "brandName": "Acme",
              "startsAt": "2025-01-01T00:00:00Z",
              "endsAt": "2025-01-15T00:00:00Z"
            }
            """;

    @Test
    void createsCampaign() throws Exception {
        mockMvc.perform(post("/api/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name").value("Spring Awareness Push"))
                .andExpect(jsonPath("$.brandName").value("Acme"))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.updatedAt", notNullValue()));
    }

    @Test
    void retrievesCampaignById() throws Exception {
        String id = createCampaignAndReturnId();

        mockMvc.perform(get("/api/campaigns/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Spring Awareness Push"));
    }

    @Test
    void returns404ForMissingCampaign() throws Exception {
        mockMvc.perform(get("/api/campaigns/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Campaign not found")));
    }

    @Test
    void rejectsEndsAtBeforeStartsAt() throws Exception {
        String body = """
                {
                  "name": "Bad Window",
                  "brandName": "Acme",
                  "startsAt": "2025-02-01T00:00:00Z",
                  "endsAt": "2025-01-01T00:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]", containsString("endsAt must be after startsAt")));
    }

    @Test
    void rejectsBlankName() throws Exception {
        String body = """
                {
                  "name": "  ",
                  "brandName": "Acme",
                  "startsAt": "2025-01-01T00:00:00Z",
                  "endsAt": "2025-01-15T00:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]", containsString("name")));
    }

    @Test
    void rejectsBlankBrandName() throws Exception {
        String body = """
                {
                  "name": "Valid Name",
                  "brandName": "",
                  "startsAt": "2025-01-01T00:00:00Z",
                  "endsAt": "2025-01-15T00:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]", containsString("brandName")));
    }

    private String createCampaignAndReturnId() throws Exception {
        String response = mockMvc.perform(post("/api/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return node.get("id").asText();
    }
}
