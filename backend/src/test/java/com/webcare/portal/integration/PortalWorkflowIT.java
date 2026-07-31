package com.webcare.portal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "webcare.audit.allow-private-addresses=true")
class PortalWorkflowIT {

    private static final String ADMIN_EMAIL = "admin@webcare.local";
    private static final String ADMIN_PASSWORD = "WebcareAdmin!2026";
    private static final String VIEWER_EMAIL = "viewer@webcare.local";
    private static final String VIEWER_PASSWORD = "WebcareViewer!2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void completesPortalWorkflowAgainstPostgresAndControlledSite() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(ADMIN_EMAIL, "wrong-password")))
                .andExpect(status().isUnauthorized());

        MockHttpSession adminSession = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        JsonNode website = responseJson(mockMvc.perform(post("/api/v1/websites")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Controlled issues",
                                "url", "http://test-site/issues/",
                                "maxPages", 10,
                                "maxDepth", 2,
                                "active", true))))
                .andExpect(status().isCreated())
                .andReturn());

        String websiteId = website.path("id").asText();
        JsonNode firstAudit = startAndWaitForAudit(adminSession, websiteId);
        assertThat(firstAudit.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(firstAudit.path("pagesChecked").asInt()).isGreaterThanOrEqualTo(4);

        JsonNode firstResults = responseJson(mockMvc.perform(get(
                                "/api/v1/audits/{auditId}/results", firstAudit.path("id").asText())
                        .session(adminSession))
                .andExpect(status().isOk())
                .andReturn());
        Set<String> ruleCodes = StreamSupport.stream(firstResults.path("findings").spliterator(), false)
                .map(node -> node.path("ruleCode").asText())
                .collect(Collectors.toSet());
        assertThat(ruleCodes).contains(
                "HTTP_STATUS_ERROR",
                "REDIRECT_CHAIN",
                "BROKEN_LINK",
                "TITLE_MISSING",
                "META_DESCRIPTION_MISSING",
                "MULTIPLE_H1",
                "CANONICAL_MISSING",
                "NOINDEX",
                "IMAGE_ALT_MISSING");

        JsonNode highFindings = responseJson(mockMvc.perform(get(
                                "/api/v1/audits/{auditId}/results", firstAudit.path("id").asText())
                        .param("severity", "HIGH")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(highFindings.path("filteredFindings").asInt()).isPositive();
        assertThat(highFindings.path("filteredFindings").asInt())
                .isLessThan(firstResults.path("totalFindings").asInt());

        String findingId = firstResults.path("findings").get(0).path("id").asText();
        JsonNode users = responseJson(mockMvc.perform(get("/api/v1/users").session(adminSession))
                .andExpect(status().isOk())
                .andReturn());
        String specialistId = StreamSupport.stream(users.spliterator(), false)
                .filter(user -> StreamSupport.stream(user.path("roles").spliterator(), false)
                        .anyMatch(role -> role.asText().equals("SPECIALIST")))
                .findFirst()
                .orElseThrow()
                .path("id").asText();

        JsonNode task = responseJson(mockMvc.perform(post("/api/v1/findings/{findingId}/tasks", findingId)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn());
        String taskId = task.path("id").asText();
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("title", task.path("title").asText());
        update.put("description", task.path("description").asText(""));
        update.put("priority", "HIGH");
        update.put("status", "IN_PROGRESS");
        update.put("assignedToUserId", specialistId);
        update.put("dueAt", Instant.now().plus(7, ChronoUnit.DAYS).toString());
        update.put("comment", "Интеграционный тест принял задачу в работу.");
        JsonNode updatedTask = responseJson(mockMvc.perform(put("/api/v1/tasks/{taskId}", taskId)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(updatedTask.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(updatedTask.path("history")).hasSize(2);

        JsonNode secondAudit = startAndWaitForAudit(adminSession, websiteId);
        JsonNode comparison = responseJson(mockMvc.perform(get(
                                "/api/v1/websites/{websiteId}/audit-comparison", websiteId)
                        .session(adminSession))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(comparison.path("previousAudit").path("id").asText())
                .isEqualTo(firstAudit.path("id").asText());
        assertThat(comparison.path("latestAudit").path("id").asText())
                .isEqualTo(secondAudit.path("id").asText());
        assertThat(comparison.path("newFindings")).isEmpty();
        assertThat(comparison.path("fixedFindings")).isEmpty();
        assertThat(comparison.path("ongoingFindings")).isNotEmpty();

        JsonNode ongoingWithTask = StreamSupport.stream(
                        comparison.path("ongoingFindings").spliterator(), false)
                .filter(finding -> finding.hasNonNull("taskId"))
                .findFirst()
                .orElseThrow();
        assertThat(ongoingWithTask.path("taskId").asText()).isEqualTo(taskId);
        mockMvc.perform(post("/api/v1/findings/{findingId}/tasks", ongoingWithTask.path("id").asText())
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());

        MockHttpSession viewerSession = login(VIEWER_EMAIL, VIEWER_PASSWORD);
        mockMvc.perform(get("/api/v1/tasks").session(viewerSession))
                .andExpect(status().isOk());
        update.put("status", "DONE");
        mockMvc.perform(put("/api/v1/tasks/{taskId}", taskId)
                        .session(viewerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden());

        JsonNode dashboard = responseJson(mockMvc.perform(get("/api/v1/dashboard").session(adminSession))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(dashboard.path("completedAudits").asInt()).isEqualTo(2);
        assertThat(dashboard.path("openTasks").asInt()).isEqualTo(1);
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        HttpSession session = result.getRequest().getSession(false);
        assertThat(session).isInstanceOf(MockHttpSession.class);
        return (MockHttpSession) session;
    }

    private JsonNode startAndWaitForAudit(MockHttpSession session, String websiteId) throws Exception {
        JsonNode started = responseJson(mockMvc.perform(post("/api/v1/websites/{websiteId}/audits", websiteId)
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andReturn());
        String auditId = started.path("id").asText();
        JsonNode audit = started;
        for (int attempt = 0; attempt < 100; attempt++) {
            MvcResult result = mockMvc.perform(get("/api/v1/audits/{auditId}", auditId).session(session))
                    .andExpect(status().isOk())
                    .andReturn();
            audit = responseJson(result);
            if (Set.of("COMPLETED", "FAILED").contains(audit.path("status").asText())) {
                break;
            }
            Thread.sleep(100);
        }
        assertThat(audit.path("status").asText())
                .withFailMessage("Audit ended as %s: %s", audit.path("status"), audit.path("failureMessage"))
                .isEqualTo("COMPLETED");
        return audit;
    }

    private String loginBody(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email, "password", password));
    }

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
