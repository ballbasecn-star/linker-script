package com.linkscript.core.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkscript.core.tag.TagService;
import com.linkscript.core.vector.EmbeddingService;
import com.linkscript.domain.dto.ScriptReviewDto;
import com.linkscript.domain.entity.FragmentType;
import com.linkscript.domain.entity.ScriptEntity;
import com.linkscript.domain.entity.TagCategory;
import com.linkscript.domain.repository.LogicFragmentJdbcRepository;
import com.linkscript.domain.repository.LogicFragmentRepository;
import com.linkscript.domain.repository.ScriptRepository;
import com.linkscript.infra.ai.AiGateway;
import com.linkscript.infra.exception.NotFoundException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
            {"fragments":[{"type":"HOOK","content":"...","logicDesc":"...","confidence":0.9}]}
            type 只能是 HOOK、SETUP、VALUE、BODY、CTA、TWIST。
            confidence 取值 0-1，表示你对该切分的把握度。
            """;

    private static final String TAGGING_SYSTEM_PROMPT = """
            你是内容分类专家。根据文案内容打标签，只返回 JSON，不要解释。
            输出格式：
            {"industry":["行业标签1"],"emotion":["情绪标签1","情绪标签2"],"audience":["受众类型"]}
            industry 示例：职场、美食、科技、教育、娱乐、健身、金融、母婴、旅行
            emotion 示例：焦虑、爽、暖、怕、好奇、搞笑、励志、感动
            audience 示例：小白、专业人士、学生、宝妈、上班族
            每个维度 1-3 个标签即可，精准优先。
            """;

    private final ScriptRepository scriptRepository;
    private final LogicFragmentRepository logicFragmentRepository;
    private final LogicFragmentJdbcRepository logicFragmentJdbcRepository;
    private final EmbeddingService embeddingService;
    private final FallbackScriptAnalyzer fallbackScriptAnalyzer;
    private final ContentReviewService contentReviewService;
    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;
    private final TagService tagService;

    public AnalysisService(
            ScriptRepository scriptRepository,
            LogicFragmentRepository logicFragmentRepository,
            LogicFragmentJdbcRepository logicFragmentJdbcRepository,
            EmbeddingService embeddingService,
            FallbackScriptAnalyzer fallbackScriptAnalyzer,
            ContentReviewService contentReviewService,
            AiGateway aiGateway,
            ObjectMapper objectMapper,
            TagService tagService) {
        this.scriptRepository = scriptRepository;
        this.logicFragmentRepository = logicFragmentRepository;
        this.logicFragmentJdbcRepository = logicFragmentJdbcRepository;
        this.embeddingService = embeddingService;
        this.fallbackScriptAnalyzer = fallbackScriptAnalyzer;
        this.contentReviewService = contentReviewService;
        this.aiGateway = aiGateway;
        this.objectMapper = objectMapper;
        this.tagService = tagService;
    }

    @Transactional
    public void analyzeScript(String scriptUuid) {
        ScriptEntity script = scriptRepository.findByScriptUuid(scriptUuid)
                .orElseThrow(() -> new NotFoundException("Script not found: " + scriptUuid));

        log.info("analysis.pipeline.begin scriptUuid={} title={} contentChars={}",
                scriptUuid,
                defaultString(script.getTitle()),
                effectiveContent(script).length());
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
                    embeddingService.toVectorLiteral(embedding),
                    fragment.confidence());
        }
        log.info("analysis.pipeline.end scriptUuid={} persistedFragments={}", scriptUuid, uniqueFragments.size());

        ScriptReviewDto review = contentReviewService.review(script, uniqueFragments);
        script.setReviewJson(writeJson(review));
        scriptRepository.save(script);
        log.info("analysis.review.persisted scriptUuid={} featuredCandidate={} overallScore={}",
                scriptUuid,
                review.featuredCandidate(),
                review.overallScore());

        // Auto-tagging phase
        autoTag(scriptUuid, script);
    }

    private void autoTag(String scriptUuid, ScriptEntity script) {
        try {
            String userPrompt = """
                    标题：%s
                    文案：%s
                    """.formatted(defaultString(script.getTitle()), effectiveContent(script));

            aiGateway.chat(TAGGING_SYSTEM_PROMPT, userPrompt)
                    .ifPresent(raw -> {
                        Map<TagCategory, List<String>> tags = parseTaggingResult(raw);
                        if (!tags.isEmpty()) {
                            tagService.tagScript(scriptUuid, tags, "AI");
                            log.info("analysis.auto_tag.success scriptUuid={} tagCount={}",
                                    scriptUuid,
                                    tags.values().stream().mapToInt(List::size).sum());
                        }
                    });
        } catch (Exception e) {
            log.warn("analysis.auto_tag.failed scriptUuid={} message={}", scriptUuid, e.getMessage());
        }
    }

    private Map<TagCategory, List<String>> parseTaggingResult(String raw) {
        Map<TagCategory, List<String>> result = new EnumMap<>(TagCategory.class);
        try {
            String cleaned = raw.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
            }
            JsonNode root = objectMapper.readTree(cleaned);
            extractTagList(root, "industry", TagCategory.INDUSTRY, result);
            extractTagList(root, "emotion", TagCategory.EMOTION, result);
            extractTagList(root, "audience", TagCategory.AUDIENCE, result);
        } catch (Exception e) {
            log.warn("analysis.auto_tag.parse_failed message={}", e.getMessage());
        }
        return result;
    }

    private void extractTagList(JsonNode root, String field, TagCategory category,
            Map<TagCategory, List<String>> result) {
        JsonNode node = root.path(field);
        if (node.isArray()) {
            List<String> tags = new ArrayList<>();
            for (JsonNode item : node) {
                String text = item.asText("").trim();
                if (!text.isEmpty()) {
                    tags.add(text);
                }
            }
            if (!tags.isEmpty()) {
                result.put(category, tags);
            }
        }
    }

    private List<AnalyzedFragment> requestAiAnalysis(ScriptEntity script) {
        String userPrompt = """
                标题：%s
                文案全文：
                %s
                请拆成 3-6 个逻辑片段，每个片段给出简短逻辑描述和置信度。
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
                double confidence = node.path("confidence").asDouble(0.5);
                if (!StringUtils.hasText(typeValue) || !StringUtils.hasText(content)) {
                    continue;
                }
                try {
                    fragments
                            .add(new AnalyzedFragment(FragmentType.valueOf(typeValue), content, logicDesc, confidence));
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

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return null;
        }
    }
}
