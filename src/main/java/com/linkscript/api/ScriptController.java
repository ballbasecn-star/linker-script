package com.linkscript.api;

import com.linkscript.core.script.ScriptService;
import com.linkscript.core.vector.VectorSearchService;
import com.linkscript.domain.dto.FragmentSearchResponse;
import com.linkscript.domain.dto.ScriptDetailResponse;
import com.linkscript.domain.entity.FragmentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class ScriptController {

    private final ScriptService scriptService;
    private final VectorSearchService vectorSearchService;

    public ScriptController(ScriptService scriptService, VectorSearchService vectorSearchService) {
        this.scriptService = scriptService;
        this.vectorSearchService = vectorSearchService;
    }

    @GetMapping("/scripts/{scriptUuid}")
    public ScriptDetailResponse getScript(@PathVariable String scriptUuid) {
        return scriptService.getDetail(scriptUuid);
    }

    @GetMapping("/fragments/search")
    public List<FragmentSearchResponse> search(
            @RequestParam String topic,
            @RequestParam(required = false) FragmentType type,
            @RequestParam(defaultValue = "3") @Min(1) @Max(10) int limit
    ) {
        return vectorSearchService.search(topic, type, limit);
    }
}
