package com.linkscript.core.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkscript.core.vector.EmbeddingService;
import com.linkscript.domain.entity.FragmentType;
import com.linkscript.domain.entity.ScriptEntity;
import com.linkscript.domain.repository.LogicFragmentJdbcRepository;
import com.linkscript.domain.repository.LogicFragmentRepository;
import com.linkscript.domain.repository.ScriptRepository;
import com.linkscript.infra.ai.AiGateway;
import com.linkscript.infra.exception.NotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private static final String ANALYSIS_SYSTEM_PROMPT = """
            你是短视频爆款脚本分析师。请将输入文案拆成结构化逻辑片段。
            只返回 JSON，不要加 Markdown 代码块，不要解释。
            输出格式：
            {"fragments":[{"type":"HOOK","content":"...","logicDesc":"..."},{"type":"SETUP","content":"...","logicDesc":"..."}]}
            type 只能是 HOOK、SETUP、VALUE、BODY、CTA、TWIST。
            """;

    private final ScriptRepository scriptRepository;
    private final LogicFragmentRepository logicFragmentRepository;
    private final LogicFragmentJdbcRepository logicFragmentJdbcRepository;
    private final EmbeddingService embeddingService;
    private final FallbackScriptAnalyzer fallbackScriptAnalyzer;
    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;

    public AnalysisService(
            ScriptRepository scriptRepository,
            LogicFragmentRepository logicFragmentRepository,
            LogicFragmentJdbcRepository logicFragmentJdbcRepository,
            EmbeddingService embeddingService,
            FallbackScriptAnalyzer fallbackScriptAnalyzer,
            AiGateway aiGateway,
            ObjectMapper objectMapper
    ) {
        this.scriptRepository = scriptRepository;
        this.logicFragmentRepository = logicFragmentRepository;
        this.logicFragmentJdbcRepository = logicFragmentJdbcRepository;
        this.embeddingService = embeddingService;
        this.fallbackScriptAnalyzer = fallbackScriptAnalyzer;
        this.aiGateway = aiGateway;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void analyzeScript(String scriptUuid) {
        ScriptEntity script = scriptRepository.findByScriptUuid(scriptUuid)
                .orElseThrow(() -> new NotFoundException("Script not found: " + scriptUuid));

        log.info("analysis.pipeline.begin scriptUuid={} title={} contentChars={}",
                scriptUuid,
                defaultString(script.getTitle()),
                effectiveContent(script).length()
        );
        List<AnalyzedFragment> fragments = requestAiAnalysis(script);
        if (fragments.isEmpty()) {
            log.info("analysis.pipeline.fallback scriptUuid={} reason=empty_ai_result", scriptUuid);
            fragments = fallbackScriptAnalyzer.analyze(script.getTitle(), effectiveContent(script));
        } else {
            log.info("analysis.pipeline.ai_result scriptUuid={} fragments={}", scriptUuid, fragments.size());
        }

        List<AnalyzedFragment> uniqueFragments = deduplicate(fragments);
        log.info("analysis.pipeline.persist scriptUuid={} uniqueFragments={}", scriptUuid, uniqueFragments.size());
        logicFragmentRepository.deleteByScriptUuid(scriptUuid);
        for (AnalyzedFragment fragment : uniqueFragments) {
            float[] embedding = embeddingService.embed(fragment.content() + "\n" + defaultString(fragment.logicDesc()));
            logicFragmentJdbcRepository.insert(
                    scriptUuid,
                    fragment.type(),
                    fragment.content(),
                    fragment.logicDesc(),
                    embeddingService.toVectorLiteral(embedding)
            );
        }
        log.info("analysis.pipeline.end scriptUuid={} persistedFragments={}", scriptUuid, uniqueFragments.size());
    }

    private List<AnalyzedFragment> requestAiAnalysis(ScriptEntity script) {
        String userPrompt = """
                标题：%s
                文案全文：
                %s
                请拆成 3-6 个逻辑片段，每个片段给出简短逻辑描述。
                """.formatted(defaultString(script.getTitle()), effectiveContent(script));

        return aiGateway.chat(ANALYSIS_SYSTEM_PROMPT, userPrompt)
                .map(this::parseFragments)
                .orElseGet(List::of);
    }

    private List<AnalyzedFragment> parseFragments(String raw) {
        if (!StringUtils.hasText(raw)) {
            log.warn("analysis.ai.parse.empty");
            return List.of();
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        try {
            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode fragmentsNode = root.path("fragments");
            if (!fragmentsNode.isArray()) {
                return List.of();
            }
            List<AnalyzedFragment> fragments = new ArrayList<>();
            for (JsonNode node : fragmentsNode) {
                String typeValue = node.path("type").asText("").trim().toUpperCase(Locale.ROOT);
                String content = node.path("content").asText("").trim();
                String logicDesc = node.path("logicDesc").asText("").trim();
                if (!StringUtils.hasText(typeValue) || !StringUtils.hasText(content)) {
                    continue;
                }
                try {
                    fragments.add(new AnalyzedFragment(FragmentType.valueOf(typeValue), content, logicDesc));
                } catch (IllegalArgumentException ignored) {
                    // Ignore invalid types returned by model.
                }
            }
            log.info("analysis.ai.parse.success fragments={}", fragments.size());
            return fragments;
        } catch (Exception exception) {
            log.warn("analysis.ai.parse.failed message={}", exception.getMessage());
            return List.of();
        }
    }

    private List<AnalyzedFragment> deduplicate(List<AnalyzedFragment> fragments) {
        Set<String> keys = new LinkedHashSet<>();
        List<AnalyzedFragment> unique = new ArrayList<>();
        for (AnalyzedFragment fragment : fragments) {
            if (!StringUtils.hasText(fragment.content())) {
                continue;
            }
            String key = fragment.type().name() + "::" + fragment.content().trim();
            if (keys.add(key)) {
                unique.add(fragment);
            }
        }
        return unique;
    }

    private String effectiveContent(ScriptEntity script) {
        if (StringUtils.hasText(script.getContent())) {
            return script.getContent();
        }
        return defaultString(script.getTranscript());
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
