package com.linkscript.domain.dto;

import com.linkscript.domain.entity.FragmentType;

public record FragmentSearchResponse(
        String scriptUuid,
        String title,
        FragmentType type,
        String content,
        String logicDesc,
        double score
) {
}
