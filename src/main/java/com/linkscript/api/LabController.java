package com.linkscript.api;

import com.linkscript.core.generation.GenerationService;
import com.linkscript.domain.dto.GenerateCompositionRequest;
import com.linkscript.domain.dto.GenerateCompositionResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/compositions")
public class LabController {

    private final GenerationService generationService;

    public LabController(GenerationService generationService) {
        this.generationService = generationService;
    }

    @PostMapping("/generate")
    public GenerateCompositionResponse generate(@Valid @RequestBody GenerateCompositionRequest request) {
        return generationService.generate(request);
    }
}
