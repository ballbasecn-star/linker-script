package com.linkscript.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record GenerationOptions(
        @Size(max = 40) String tone,
        @Min(50) @Max(1200) Integer length
) {
}
