package com.linkscript.core.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkscript.core.analysis.AnalysisRequestedEvent;
import com.linkscript.domain.dto.IngestScriptRequest;
import com.linkscript.domain.dto.IngestScriptResponse;
import com.linkscript.domain.dto.LogicFragmentDto;
import com.linkscript.domain.dto.ScriptDetailResponse;
import com.linkscript.domain.entity.LogicFragmentEntity;
import com.linkscript.domain.entity.ScriptEntity;
import com.linkscript.domain.entity.ScriptStatus;
import com.linkscript.domain.repository.LogicFragmentRepository;
import com.linkscript.domain.repository.ScriptRepository;
import com.linkscript.infra.exception.NotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.linkscript.infra.logging.RequestTraceFilter;

@Service
public class ScriptService {

    private final ScriptRepository scriptRepository;
    private final LogicFragmentRepository logicFragmentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public ScriptService(
            ScriptRepository scriptRepository,
            LogicFragmentRepository logicFragmentRepository,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper
    ) {
        this.scriptRepository = scriptRepository;
        this.logicFragmentRepository = logicFragmentRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
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
        entity.setStatus(ScriptStatus.PENDING);
        ScriptEntity saved = scriptRepository.save(entity);
        eventPublisher.publishEvent(new AnalysisRequestedEvent(
                saved.getScriptUuid(),
                MDC.get(RequestTraceFilter.REQUEST_ID)
        ));
        return new IngestScriptResponse(saved.getScriptUuid(), saved.getStatus());
    }

    @Transactional(readOnly = true)
    public ScriptDetailResponse getDetail(String scriptUuid) {
        ScriptEntity script = scriptRepository.findByScriptUuid(scriptUuid)
                .orElseThrow(() -> new NotFoundException("Script not found: " + scriptUuid));
        List<LogicFragmentDto> fragments = logicFragmentRepository.findByScriptUuidOrderByIdAsc(scriptUuid).stream()
                .map(this::toFragmentDto)
                .toList();
        return new ScriptDetailResponse(
                script.getScriptUuid(),
                script.getTitle(),
                script.getContent(),
                script.getTranscript(),
                script.getSourcePlatform(),
                script.getExternalId(),
                readJson(script.getStatsJson()),
                script.getStatus(),
                script.getCreatedAt(),
                fragments
        );
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
                    request.externalId()
            );
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        ScriptEntity entity = new ScriptEntity();
        entity.setScriptUuid(UUID.randomUUID().toString());
        return entity;
    }

    private LogicFragmentDto toFragmentDto(LogicFragmentEntity fragment) {
        return new LogicFragmentDto(fragment.getFragmentType(), fragment.getContent(), fragment.getLogicDesc());
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
}
