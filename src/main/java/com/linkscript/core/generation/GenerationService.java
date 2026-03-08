package com.linkscript.core.generation;

import com.linkscript.core.vector.VectorSearchService;
import com.linkscript.domain.dto.FragmentSearchResponse;
import com.linkscript.domain.dto.GenerateCompositionRequest;
import com.linkscript.domain.dto.GenerateCompositionResponse;
import com.linkscript.domain.dto.GenerationOptions;
import com.linkscript.domain.entity.FragmentType;
import com.linkscript.domain.entity.GenerationLogEntity;
import com.linkscript.domain.entity.LogicFragmentEntity;
import com.linkscript.domain.repository.GenerationLogRepository;
import com.linkscript.domain.repository.LogicFragmentRepository;
import com.linkscript.infra.ai.AiGateway;
import com.linkscript.infra.exception.BadRequestException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

    private static final String GENERATION_SYSTEM_PROMPT = """
            你是中文短视频爆款编剧。输出要有网感、节奏感和明确结构。
            请参考样本的逻辑骨架，不要照抄原文。
            优先输出 1 条完整可发布脚本，结构包含：开场钩子、悬念铺垫、价值交付、行动号召。
            """;

    private final VectorSearchService vectorSearchService;
    private final LogicFragmentRepository logicFragmentRepository;
    private final GenerationLogRepository generationLogRepository;
    private final AiGateway aiGateway;

    public GenerationService(
            VectorSearchService vectorSearchService,
            LogicFragmentRepository logicFragmentRepository,
            GenerationLogRepository generationLogRepository,
            AiGateway aiGateway
    ) {
        this.vectorSearchService = vectorSearchService;
        this.logicFragmentRepository = logicFragmentRepository;
        this.generationLogRepository = generationLogRepository;
        this.aiGateway = aiGateway;
    }

    @Transactional
    public GenerateCompositionResponse generate(GenerateCompositionRequest request) {
        List<String> referenceUuids = resolveSampleUuids(request);
        log.info("generation.begin topic={} requestedSamples={} resolvedSamples={}",
                request.topic(),
                request.sampleUuids() == null ? 0 : request.sampleUuids().size(),
                referenceUuids.size()
        );
        List<LogicFragmentEntity> fragments = logicFragmentRepository.findByScriptUuidInOrderByScriptUuidAscIdAsc(referenceUuids);
        if (fragments.isEmpty()) {
            throw new BadRequestException("No sample fragments available for generation");
        }

        String prompt = buildPrompt(request, referenceUuids, fragments);
        String content = aiGateway.chat(GENERATION_SYSTEM_PROMPT, prompt)
                .filter(StringUtils::hasText)
                .map(result -> {
                    log.info("generation.content source=ai topic={} chars={}", request.topic(), result.length());
                    return result;
                })
                .orElseGet(() -> {
                    String fallback = fallbackCompose(request, referenceUuids, fragments);
                    log.info("generation.content source=fallback topic={} chars={}", request.topic(), fallback.length());
                    return fallback;
                });

        GenerationLogEntity logEntity = new GenerationLogEntity();
        logEntity.setTopic(request.topic());
        logEntity.setReferenceUuids(String.join(",", referenceUuids));
        logEntity.setAiOutput(content);
        GenerationLogEntity saved = generationLogRepository.save(logEntity);
        log.info("generation.completed topic={} logId={} referenceUuids={}",
                request.topic(),
                saved.getId(),
                String.join(",", referenceUuids)
        );

        return new GenerateCompositionResponse(saved.getId(), request.topic(), referenceUuids, content);
    }

    private List<String> resolveSampleUuids(GenerateCompositionRequest request) {
        List<String> sampleUuids = cleanUuids(request.sampleUuids());
        if (!sampleUuids.isEmpty()) {
            return sampleUuids;
        }

        return vectorSearchService.search(request.topic(), FragmentType.HOOK, 3).stream()
                .map(FragmentSearchResponse::scriptUuid)
                .distinct()
                .limit(3)
                .toList();
    }

    private List<String> cleanUuids(List<String> sampleUuids) {
        if (sampleUuids == null) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String uuid : sampleUuids) {
            if (StringUtils.hasText(uuid)) {
                unique.add(uuid.trim());
            }
        }
        return new ArrayList<>(unique);
    }

    private String buildPrompt(GenerateCompositionRequest request, List<String> referenceUuids, List<LogicFragmentEntity> fragments) {
        Map<String, List<LogicFragmentEntity>> grouped = fragments.stream()
                .collect(Collectors.groupingBy(LogicFragmentEntity::getScriptUuid));

        StringBuilder builder = new StringBuilder();
        builder.append("创作主题：").append(request.topic()).append('\n');
        builder.append("期望语气：").append(resolveTone(request.options())).append('\n');
        builder.append("目标字数：").append(resolveLength(request.options())).append('\n');
        builder.append("参考样本：").append(String.join(", ", referenceUuids)).append("\n\n");
        for (String scriptUuid : referenceUuids) {
            builder.append("样本 ").append(scriptUuid).append('\n');
            List<LogicFragmentEntity> sampleFragments = grouped.getOrDefault(scriptUuid, List.of());
            for (LogicFragmentEntity fragment : sampleFragments) {
                builder.append("- ")
                        .append(fragment.getFragmentType().name())
                        .append(": ")
                        .append(fragment.getContent())
                        .append("（逻辑：")
                        .append(defaultString(fragment.getLogicDesc()))
                        .append("）\n");
            }
            builder.append('\n');
        }
        builder.append("请输出 1 条完整脚本，保留结构标题，便于后续继续编辑。");
        return builder.toString();
    }

    private String fallbackCompose(GenerateCompositionRequest request, List<String> referenceUuids, List<LogicFragmentEntity> fragments) {
        Map<FragmentType, List<String>> byType = fragments.stream()
                .collect(Collectors.groupingBy(
                        LogicFragmentEntity::getFragmentType,
                        Collectors.mapping(LogicFragmentEntity::getContent, Collectors.toList())
                ));

        String hookSeed = firstOrDefault(byType.get(FragmentType.HOOK), "如果你还在用老办法做「" + request.topic() + "」，这条脚本会帮你换一个更能打的切口。");
        String setupSeed = firstOrDefault(byType.get(FragmentType.SETUP), "多数人讲这个主题时，一上来就堆信息，观众根本不想继续看。");
        String valueSeed = firstOrDefault(byType.get(FragmentType.VALUE), "真正有效的做法，是先把冲突拉满，再给出具体可执行的解决动作。");
        String ctaSeed = firstOrDefault(byType.get(FragmentType.CTA), "如果你想要我继续拆这个主题，评论区留关键词，我把下一版模版也给你。");

        return """
                【创作主题】
                %s

                【参考样本】
                %s

                【完整脚本】
                开场钩子：%s

                悬念铺垫：围绕“%s”这个主题，很多内容的问题不是没干货，而是前 3 秒没有把人留下来。%s

                价值交付：你可以直接套这个节奏来讲。第一句先抛出反常识结论；第二句补一刀用户痛点；第三句再把真正有效的方法说透。%s

                行动号召：%s
                """.formatted(
                request.topic(),
                String.join(", ", referenceUuids),
                adaptSentence(hookSeed, request.topic(), resolveTone(request.options())),
                request.topic(),
                adaptSentence(setupSeed, request.topic(), resolveTone(request.options())),
                adaptSentence(valueSeed, request.topic(), resolveTone(request.options())),
                adaptSentence(ctaSeed, request.topic(), resolveTone(request.options()))
        );
    }

    private String resolveTone(GenerationOptions options) {
        if (options == null || !StringUtils.hasText(options.tone())) {
            return "有网感、利落";
        }
        return options.tone();
    }

    private int resolveLength(GenerationOptions options) {
        if (options == null || options.length() == null) {
            return 300;
        }
        return options.length();
    }

    private String firstOrDefault(List<String> values, String defaultValue) {
        if (values == null || values.isEmpty()) {
            return defaultValue;
        }
        return values.getFirst();
    }

    private String adaptSentence(String source, String topic, String tone) {
        if (!StringUtils.hasText(source)) {
            return "围绕「%s」做一条 %s 风格的新脚本。".formatted(topic, tone);
        }
        return source.replace("这个主题", topic);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
