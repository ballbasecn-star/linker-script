package com.linkscript.core.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkscript.domain.dto.ScriptReviewDto;
import com.linkscript.domain.entity.ScriptEntity;
import com.linkscript.infra.ai.AiGateway;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ContentReviewService {

    private static final Logger log = LoggerFactory.getLogger(ContentReviewService.class);

    private static final String REVIEW_SYSTEM_PROMPT = """
            你是短视频内容评审助手。请根据公开可见的优质内容标准，对文案做“精选候选点评”。
            注意：
            1. “精选候选”是内部判断，不代表平台官方最终结论。
            2. 重点评估：真实、专业、可信、有趣，及内容完整度、获得感、惊喜感。
            3. 如发现夸大、绝对化、虚假承诺、信息空泛等问题，要明确指出。
            只返回 JSON，不要加 Markdown 代码块，不要解释。
            输出格式：
            {
              "featuredCandidate": true,
              "featuredProbability": 0.84,
              "featuredConclusion": "具备精选候选特征",
              "featuredReason": "结构完整、信息增量明确",
              "complete": true,
              "completenessScore": 86,
              "gainScore": 88,
              "surpriseScore": 72,
              "authenticityScore": 80,
              "professionalismScore": 82,
              "credibilityScore": 79,
              "interestingnessScore": 74,
              "overallScore": 82,
              "summary": "整体内容有明确价值交付",
              "highlights": ["亮点1"],
              "issues": ["问题1"],
              "suggestions": ["建议1"],
              "riskFlags": ["风险1"]
            }
            所有 score 取值 0-100。
            """;

    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;
    private final FallbackContentReviewer fallbackContentReviewer;

    public ContentReviewService(
            AiGateway aiGateway,
            ObjectMapper objectMapper,
            FallbackContentReviewer fallbackContentReviewer) {
        this.aiGateway = aiGateway;
        this.objectMapper = objectMapper;
        this.fallbackContentReviewer = fallbackContentReviewer;
    }

    public ScriptReviewDto review(ScriptEntity script, List<AnalyzedFragment> fragments) {
        return requestAiReview(script, fragments)
                .orElseGet(() -> fallbackContentReviewer.review(script, fragments));
    }

    private Optional<ScriptReviewDto> requestAiReview(ScriptEntity script, List<AnalyzedFragment> fragments) {
        String prompt = buildPrompt(script, fragments);
        return aiGateway.chat(REVIEW_SYSTEM_PROMPT, prompt)
                .flatMap(this::parseReview);
    }

    private String buildPrompt(ScriptEntity script, List<AnalyzedFragment> fragments) {
        StringBuilder builder = new StringBuilder();
        builder.append("标题：").append(defaultString(script.getTitle())).append('\n');
        builder.append("正文：").append(defaultString(effectiveContent(script))).append('\n');
        builder.append("热度等级：").append(defaultString(script.getHeatLevel())).append('\n');
        builder.append("逻辑碎片：\n");
        for (AnalyzedFragment fragment : fragments) {
            builder.append("- ")
                    .append(fragment.type().name())
                    .append(": ")
                    .append(fragment.content())
                    .append("（逻辑：")
                    .append(defaultString(fragment.logicDesc()))
                    .append("，置信度：")
                    .append(fragment.confidence())
                    .append("）\n");
        }
        builder.append("请给出精选候选点评，并指出内容是否完整、是否让观众有获得感、惊喜感。");
        return builder.toString();
    }

    private Optional<ScriptReviewDto> parseReview(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }

        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }

        try {
            JsonNode root = objectMapper.readTree(cleaned);
            ScriptReviewDto dto = new ScriptReviewDto(
                    root.path("featuredCandidate").asBoolean(false),
                    clampProbability(root.path("featuredProbability").asDouble(0.5)),
                    text(root, "featuredConclusion"),
                    text(root, "featuredReason"),
                    root.path("complete").asBoolean(false),
                    clampScore(root.path("completenessScore").asInt(0)),
                    clampScore(root.path("gainScore").asInt(0)),
                    clampScore(root.path("surpriseScore").asInt(0)),
                    clampScore(root.path("authenticityScore").asInt(0)),
                    clampScore(root.path("professionalismScore").asInt(0)),
                    clampScore(root.path("credibilityScore").asInt(0)),
                    clampScore(root.path("interestingnessScore").asInt(0)),
                    clampScore(root.path("overallScore").asInt(0)),
                    text(root, "summary"),
                    stringList(root.path("highlights")),
                    stringList(root.path("issues")),
                    stringList(root.path("suggestions")),
                    stringList(root.path("riskFlags")));
            log.info("analysis.review.ai_success overallScore={} featuredCandidate={}",
                    dto.overallScore(), dto.featuredCandidate());
            return Optional.of(dto);
        } catch (Exception exception) {
            log.warn("analysis.review.ai_parse_failed message={}", exception.getMessage());
            return Optional.empty();
        }
    }

    private List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                String text = item.asText("").trim();
                if (!text.isEmpty()) {
                    values.add(text);
                }
            }
        }
        return values;
    }

    private String text(JsonNode root, String field) {
        return root.path(field).asText("").trim();
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private double clampProbability(double score) {
        double normalized = Math.max(0.0, Math.min(1.0, score));
        return Math.round(normalized * 100.0) / 100.0;
    }

    private String effectiveContent(ScriptEntity script) {
        if (StringUtils.hasText(script.getContent())) {
            return script.getContent();
        }
        return script.getTranscript() == null ? "" : script.getTranscript();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
