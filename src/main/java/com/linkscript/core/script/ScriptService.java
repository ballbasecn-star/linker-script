package com.linkscript.core.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkscript.core.analysis.AnalysisRequestedEvent;
import com.linkscript.core.score.HeatScoreCalculator;
import com.linkscript.core.tag.TagService;
import com.linkscript.domain.dto.IngestScriptRequest;
import com.linkscript.domain.dto.IngestScriptResponse;
import com.linkscript.domain.dto.LogicFragmentDto;
import com.linkscript.domain.dto.ScriptDetailResponse;
import com.linkscript.domain.dto.ScriptReviewDto;
import com.linkscript.domain.dto.TagDto;
import com.linkscript.domain.entity.LogicFragmentEntity;
import com.linkscript.domain.entity.ScriptEntity;
import com.linkscript.domain.entity.ScriptStatus;
import com.linkscript.domain.repository.LogicFragmentRepository;
import com.linkscript.domain.repository.ScriptRepository;
import com.linkscript.infra.exception.NotFoundException;
import com.linkscript.infra.logging.RequestTraceFilter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ScriptService {

    private static final Logger log = LoggerFactory.getLogger(ScriptService.class);

    private final ScriptRepository scriptRepository;
    private final LogicFragmentRepository logicFragmentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final HeatScoreCalculator heatScoreCalculator;
    private final TagService tagService;

    public ScriptService(
            ScriptRepository scriptRepository,
            LogicFragmentRepository logicFragmentRepository,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper,
            HeatScoreCalculator heatScoreCalculator,
            TagService tagService) {
        this.scriptRepository = scriptRepository;
        this.logicFragmentRepository = logicFragmentRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.heatScoreCalculator = heatScoreCalculator;
        this.tagService = tagService;
    }

    @Transactional
    public IngestScriptResponse ingest(IngestScriptRequest request) {
        ScriptEntity entity = resolveScript(request);
        entity.setTitle(request.title());
        entity.setContent(request.content());
        entity.setTranscript(request.transcript());
        entity.setSourcePlatform(request.sourcePlatform());
        entity.setExternalId(request.externalId());
        entity.setStatsJson(toJson(request.statistics()));
        entity.setReviewJson(null);
        entity.setStatus(ScriptStatus.PENDING);

        // Calculate heat score from statistics
        HeatScoreCalculator.HeatResult heat = heatScoreCalculator.calculate(request.statistics());
        entity.setHeatScore(heat.score());
        entity.setHeatLevel(heat.level());
        log.info("script.heat_score uuid={} score={} level={}", entity.getScriptUuid(), heat.score(), heat.level());

        ScriptEntity saved = scriptRepository.save(entity);
        eventPublisher.publishEvent(new AnalysisRequestedEvent(
                saved.getScriptUuid(),
                MDC.get(RequestTraceFilter.REQUEST_ID)));
        return new IngestScriptResponse(saved.getScriptUuid(), saved.getStatus());
    }

    @Transactional(readOnly = true)
    public ScriptDetailResponse getDetail(String scriptUuid) {
        ScriptEntity script = scriptRepository.findByScriptUuid(scriptUuid)
                .orElseThrow(() -> new NotFoundException("Script not found: " + scriptUuid));
        List<LogicFragmentDto> fragments = logicFragmentRepository.findByScriptUuidOrderByIdAsc(scriptUuid).stream()
                .map(this::toFragmentDto)
                .toList();
        List<TagDto> tags = tagService.getTagsByScript(scriptUuid);
        return new ScriptDetailResponse(
                script.getScriptUuid(),
                script.getTitle(),
                script.getContent(),
                script.getTranscript(),
                script.getSourcePlatform(),
                script.getExternalId(),
                readJson(script.getStatsJson()),
                script.getStatus(),
                script.getHeatScore(),
                script.getHeatLevel(),
                script.getCreatedAt(),
                readReview(script.getReviewJson()),
                fragments,
                tags);
    }

    @Transactional
    public void markStatus(String scriptUuid, ScriptStatus status) {
        ScriptEntity script = scriptRepository.findByScriptUuid(scriptUuid)
                .orElseThrow(() -> new NotFoundException("Script not found: " + scriptUuid));
        script.setStatus(status);
        scriptRepository.save(script);
    }

    @Transactional(readOnly = true)
    public Map<String, String> titlesByUuids(List<String> scriptUuids) {
        return scriptRepository.findAllByScriptUuidIn(scriptUuids).stream()
                .collect(Collectors.toMap(ScriptEntity::getScriptUuid, ScriptEntity::getTitle, (left, right) -> left));
    }

    private ScriptEntity resolveScript(IngestScriptRequest request) {
        if (StringUtils.hasText(request.externalId())) {
            Optional<ScriptEntity> existing = scriptRepository.findFirstBySourcePlatformAndExternalId(
                    request.sourcePlatform(),
                    request.externalId());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        ScriptEntity entity = new ScriptEntity();
        entity.setScriptUuid(UUID.randomUUID().toString());
        return entity;
    }

    private LogicFragmentDto toFragmentDto(LogicFragmentEntity fragment) {
        return new LogicFragmentDto(
                fragment.getId(),
                fragment.getFragmentType(),
                fragment.getContent(),
                fragment.getLogicDesc(),
                fragment.getConfidence() != null ? fragment.getConfidence() : 0);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException exception) {
            return "{}";
        }
    }

    private JsonNode readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (IOException exception) {
            return objectMapper.createObjectNode();
        }
    }

    private ScriptReviewDto readReview(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ScriptReviewDto.class);
        } catch (IOException exception) {
            return null;
        }
    }
}
