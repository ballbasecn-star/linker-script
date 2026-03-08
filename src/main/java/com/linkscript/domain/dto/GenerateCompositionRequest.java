package com.linkscript.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record GenerateCompositionRequest(
        @NotBlank String topic,
        List<String> sampleUuids,
        @Valid GenerationOptions options
) {
}
