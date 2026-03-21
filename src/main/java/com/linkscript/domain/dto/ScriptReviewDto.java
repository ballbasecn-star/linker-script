package com.linkscript.domain.dto;

import java.util.List;

public record ScriptReviewDto(
        boolean featuredCandidate,
        double featuredProbability,
        String featuredConclusion,
        String featuredReason,
        boolean complete,
        int completenessScore,
        int gainScore,
        int surpriseScore,
        int authenticityScore,
        int professionalismScore,
        int credibilityScore,
        int interestingnessScore,
        int overallScore,
        String summary,
        List<String> highlights,
        List<String> issues,
        List<String> suggestions,
        List<String> riskFlags) {
}
