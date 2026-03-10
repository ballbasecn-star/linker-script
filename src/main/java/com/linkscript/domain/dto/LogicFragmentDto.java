package com.linkscript.domain.dto;

import com.linkscript.domain.entity.FragmentType;

public record LogicFragmentDto(Long id, FragmentType type, String content, String logicDesc, double confidence) {
}
