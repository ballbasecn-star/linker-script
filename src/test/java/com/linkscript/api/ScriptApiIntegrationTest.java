package com.linkscript.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkscript.core.analysis.AnalysisService;
import com.linkscript.core.script.ScriptService;
import com.linkscript.domain.dto.IngestScriptRequest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockReset;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScriptApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ScriptService scriptService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @SpyBean(reset = MockReset.BEFORE)
    private AnalysisService analysisService;

    @Test
    void shouldReturnPagedScriptsWithTagsAndAllowManualTagging() throws Exception {
        String externalId = "api-contract-" + UUID.randomUUID();
        String ingestPayload = """
                {
                  "title": "管理层汇报别再从背景开始",
                  "content": "老板最想先听到的是结果，不是背景。很多人一开口就讲前情，讲了半天还没到重点。你应该先给结论，再补动作，最后补风险和下一步。",
                  "sourcePlatform": "douyin",
                  "externalId": "%s",
                  "statistics": {
                    "likes": 120,
                    "shares": 20
                  }
                }
                """.formatted(externalId);

        MvcResult ingestResult = mockMvc.perform(post("/api/v1/scripts/ingest")
                        .contentType(APPLICATION_JSON)
                        .content(ingestPayload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        String scriptUuid = readField(ingestResult, "scriptUuid");
        awaitScriptStatus(scriptUuid, "COMPLETED", Duration.ofSeconds(5));

        mockMvc.perform(post("/api/v1/scripts/{uuid}/tags", scriptUuid)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "tags": {
                                    "STYLE": ["克制"],
                                    "AUDIENCE": ["上班族"]
                                  }
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/scripts/{scriptUuid}", scriptUuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[*].name", Matchers.hasItems("克制", "上班族")))
                .andExpect(jsonPath("$.review").exists())
                .andExpect(jsonPath("$.review.overallScore").isNumber())
                .andExpect(jsonPath("$.review.featuredConclusion").isString());

        mockMvc.perform(get("/api/v1/scripts")
                        .param("tags", "上班族")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].scriptUuid").value(scriptUuid))
                .andExpect(jsonPath("$.content[0].tags[*].name", Matchers.hasItems("克制", "上班族")))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void shouldEnforceUniqueSourcePlatformAndExternalIdAtDatabaseLevel() {
        String externalId = "uq-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ls_script (
                    script_uuid, title, content, source_platform, external_id, status, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, "uq-script-1", "title-1", "content-1", "douyin", externalId, "PENDING");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO ls_script (
                            script_uuid, title, content, source_platform, external_id, status, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """, "uq-script-2", "title-2", "content-2", "douyin", externalId, "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldDispatchAnalysisOnlyAfterTransactionCommit() throws Exception {
        AtomicReference<String> scriptUuidRef = new AtomicReference<>();

        transactionTemplate.executeWithoutResult(status -> {
            IngestScriptRequest request = new IngestScriptRequest(
                    "事务提交前不要启动分析",
                    "真正稳的异步，不是能跑就行，而是要等事务提交后再触发后台任务。",
                    null,
                    "douyin",
                    "tx-" + UUID.randomUUID(),
                    Map.of("likes", 33, "shares", 3));

            scriptUuidRef.set(scriptService.ingest(request).scriptUuid());
            sleepSilently(300);
            verify(analysisService, never()).analyzeScript(scriptUuidRef.get());
        });

        awaitAnalysisInvocation(scriptUuidRef.get(), Duration.ofSeconds(5));
    }

    private void awaitScriptStatus(String scriptUuid, String expectedStatus, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            MvcResult result = mockMvc.perform(get("/api/v1/scripts/{scriptUuid}", scriptUuid))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
            String actualStatus = body.path("status").asText();
            if (expectedStatus.equals(actualStatus)) {
                return;
            }
            if ("FAILED".equals(actualStatus)) {
                throw new AssertionError("Async analysis failed for script " + scriptUuid);
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for status " + expectedStatus + " for script " + scriptUuid);
    }

    private void awaitAnalysisInvocation(String scriptUuid, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        AssertionError lastError = null;
        while (System.nanoTime() < deadline) {
            try {
                verify(analysisService, atLeastOnce()).analyzeScript(scriptUuid);
                return;
            } catch (AssertionError error) {
                lastError = error;
                Thread.sleep(100);
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new AssertionError("Timed out waiting for analysis invocation for script " + scriptUuid);
    }

    private String readField(MvcResult result, String field) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).path(field).asText();
    }

    private void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for async guard window", interruptedException);
        }
    }
}
