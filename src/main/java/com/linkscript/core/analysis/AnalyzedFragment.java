package com.linkscript.core.analysis;

import com.linkscript.domain.entity.FragmentType;

public record AnalyzedFragment(FragmentType type, String content, String logicDesc) {
}
