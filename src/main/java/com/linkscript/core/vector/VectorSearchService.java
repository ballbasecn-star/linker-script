package com.linkscript.core.vector;

import com.linkscript.domain.dto.FragmentSearchResponse;
import com.linkscript.domain.entity.FragmentType;
import com.linkscript.domain.entity.LogicFragmentEntity;
import com.linkscript.domain.repository.LogicFragmentJdbcRepository;
import com.linkscript.domain.repository.LogicFragmentRepository;
import com.linkscript.domain.repository.ScriptRepository;
import com.linkscript.infra.ai.VectorProperties;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class VectorSearchService {

    private final EmbeddingService embeddingService;
    private final LogicFragmentJdbcRepository logicFragmentJdbcRepository;
    private final LogicFragmentRepository logicFragmentRepository;
    private final ScriptRepository scriptRepository;
    private final VectorProperties vectorProperties;

    public VectorSearchService(
            EmbeddingService embeddingService,
            LogicFragmentJdbcRepository logicFragmentJdbcRepository,
            LogicFragmentRepository logicFragmentRepository,
            ScriptRepository scriptRepository,
            VectorProperties vectorProperties
    ) {
        this.embeddingService = embeddingService;
        this.logicFragmentJdbcRepository = logicFragmentJdbcRepository;
        this.logicFragmentRepository = logicFragmentRepository;
        this.scriptRepository = scriptRepository;
        this.vectorProperties = vectorProperties;
    }

    public List<FragmentSearchResponse> search(String topic, FragmentType type, int limit) {
        int safeLimit = Math.max(1, limit);
        float[] vector = embeddingService.embed(topic);
        String literal = embeddingService.toVectorLiteral(vector);
        try {
            return logicFragmentJdbcRepository.similaritySearch(literal, type, safeLimit).stream()
                    .map(hit -> new FragmentSearchResponse(
                            hit.scriptUuid(),
                            hit.title(),
                            hit.type(),
                            hit.content(),
                            hit.logicDesc(),
                            hit.score()
                    ))
                    .toList();
        } catch (DataAccessException exception) {
            return lexicalFallback(topic, type, safeLimit);
        }
    }

    private List<FragmentSearchResponse> lexicalFallback(String topic, FragmentType type, int limit) {
        List<LogicFragmentEntity> candidates = type == null
                ? logicFragmentRepository.findTop200ByOrderByIdDesc()
                : logicFragmentRepository.findTop200ByFragmentTypeOrderByIdDesc(type);

        Map<String, String> titles = scriptRepository.findAllByScriptUuidIn(
                        candidates.stream().map(LogicFragmentEntity::getScriptUuid).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(script -> script.getScriptUuid(), script -> script.getTitle(), (left, right) -> left));

        Set<String> topicTokens = tokenize(topic);
        return candidates.stream()
                .map(candidate -> new FragmentSearchResponse(
                        candidate.getScriptUuid(),
                        titles.getOrDefault(candidate.getScriptUuid(), ""),
                        candidate.getFragmentType(),
                        candidate.getContent(),
                        candidate.getLogicDesc(),
                        score(topic, topicTokens, candidate)
                ))
                .sorted(Comparator.comparingDouble(FragmentSearchResponse::score).reversed())
                .limit(limit)
                .toList();
    }

    private double score(String topic, Set<String> topicTokens, LogicFragmentEntity candidate) {
        String haystack = (defaultString(candidate.getContent()) + " " + defaultString(candidate.getLogicDesc()))
                .toLowerCase(Locale.ROOT);
        double score = 0;
        for (String token : topicTokens) {
            if (haystack.contains(token)) {
                score += 1.0;
            }
        }
        if (StringUtils.hasText(topic) && haystack.contains(topic.toLowerCase(Locale.ROOT))) {
            score += 1.5;
        }
        if (candidate.getFragmentType() == FragmentType.HOOK) {
            score += 0.2;
        }
        return score;
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        if (!StringUtils.hasText(text)) {
            return tokens;
        }
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        for (String token : normalized.split("\\s+")) {
            if (token.length() > 1) {
                tokens.add(token);
            }
        }
        String compact = normalized.replace(" ", "");
        for (int index = 0; index < compact.length() - 1 && tokens.size() < vectorProperties.lexicalCandidateLimit(); index++) {
            tokens.add(compact.substring(index, index + 2));
        }
        return tokens;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
