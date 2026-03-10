package com.linkscript.api;

import com.linkscript.core.script.ScriptService;
import com.linkscript.core.tag.TagService;
import com.linkscript.core.vector.VectorSearchService;
import com.linkscript.domain.dto.FragmentSearchResponse;
import com.linkscript.domain.dto.ScriptDetailResponse;
import com.linkscript.domain.entity.FragmentType;
import com.linkscript.domain.entity.LogicFragmentEntity;
import com.linkscript.domain.repository.LogicFragmentRepository;
import com.linkscript.domain.repository.ScriptRepository;
import com.linkscript.domain.entity.ScriptEntity;
import com.linkscript.infra.exception.NotFoundException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Arrays;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class ScriptController {

    private final ScriptService scriptService;
    private final VectorSearchService vectorSearchService;
    private final LogicFragmentRepository logicFragmentRepository;
    private final TagService tagService;
    private final ScriptRepository scriptRepository;

    public ScriptController(
            ScriptService scriptService,
            VectorSearchService vectorSearchService,
            LogicFragmentRepository logicFragmentRepository,
            TagService tagService,
            ScriptRepository scriptRepository) {
        this.scriptService = scriptService;
        this.vectorSearchService = vectorSearchService;
        this.logicFragmentRepository = logicFragmentRepository;
        this.tagService = tagService;
        this.scriptRepository = scriptRepository;
    }

    @GetMapping("/scripts/{scriptUuid}")
    public ScriptDetailResponse getScript(@PathVariable String scriptUuid) {
        return scriptService.getDetail(scriptUuid);
    }

    @GetMapping("/scripts")
    public List<ScriptSummaryResponse> listScripts(
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) String heatLevel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        List<String> targetUuids = null;

        // Filter by tags if provided
        if (tags != null && !tags.isBlank()) {
            List<String> tagNames = Arrays.asList(tags.split(","));
            targetUuids = tagService.findScriptUuidsByTagNames(tagNames);
            if (targetUuids.isEmpty()) {
                return List.of();
            }
        }

        // Filter by heat level if provided
        List<String> heatLevels = null;
        if (heatLevel != null && !heatLevel.isBlank()) {
            heatLevels = Arrays.asList(heatLevel.split(","));
        }

        List<ScriptEntity> scripts;
        if (targetUuids != null && heatLevels != null) {
            scripts = scriptRepository.findByScriptUuidInAndHeatLevelIn(targetUuids, heatLevels,
                    PageRequest.of(page, size));
        } else if (targetUuids != null) {
            scripts = scriptRepository.findByScriptUuidIn(targetUuids, PageRequest.of(page, size));
        } else if (heatLevels != null) {
            scripts = scriptRepository.findByHeatLevelIn(heatLevels, PageRequest.of(page, size));
        } else {
            scripts = scriptRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        }

        return scripts.stream()
                .map(s -> new ScriptSummaryResponse(
                        s.getScriptUuid(),
                        s.getTitle(),
                        s.getSourcePlatform(),
                        s.getStatus().name(),
                        s.getHeatScore(),
                        s.getHeatLevel(),
                        s.getCreatedAt()))
                .toList();
    }

    @GetMapping("/fragments/search")
    public List<FragmentSearchResponse> search(
            @RequestParam String topic,
            @RequestParam(required = false) FragmentType type,
            @RequestParam(defaultValue = "3") @Min(1) @Max(10) int limit) {
        return vectorSearchService.search(topic, type, limit);
    }

    @PutMapping("/fragments/{id}")
    public UpdateFragmentResponse updateFragment(@PathVariable Long id, @RequestBody UpdateFragmentRequest request) {
        LogicFragmentEntity fragment = logicFragmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Fragment not found: " + id));
        if (request.type() != null) {
            fragment.setFragmentType(request.type());
        }
        if (request.content() != null) {
            fragment.setContent(request.content());
        }
        if (request.logicDesc() != null) {
            fragment.setLogicDesc(request.logicDesc());
        }
        LogicFragmentEntity saved = logicFragmentRepository.save(fragment);
        return new UpdateFragmentResponse(saved.getId(), saved.getFragmentType(), saved.getContent(),
                saved.getLogicDesc());
    }

    public record UpdateFragmentRequest(FragmentType type, String content, String logicDesc) {
    }

    public record UpdateFragmentResponse(Long id, FragmentType type, String content, String logicDesc) {
    }

    public record ScriptSummaryResponse(
            String scriptUuid,
            String title,
            String sourcePlatform,
            String status,
            Double heatScore,
            String heatLevel,
            java.time.LocalDateTime createdAt) {
    }
}
