package com.linkscript.core.analysis;

import com.linkscript.domain.dto.ScriptReviewDto;
import com.linkscript.domain.entity.FragmentType;
import com.linkscript.domain.entity.ScriptEntity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FallbackContentReviewer {

    private static final List<String> GAIN_MARKERS = List.of(
            "方法", "步骤", "模板", "清单", "建议", "核心", "结论", "记住", "第一", "第二", "第三", "如何", "怎么");
    private static final List<String> SURPRISE_MARKERS = List.of(
            "但是", "其实", "反而", "没想到", "结果", "别再", "不是", "而是", "反转", "居然");
    private static final List<String> RISK_MARKERS = List.of(
            "保证", "绝对", "100%", "立刻", "马上", "躺赚", "无脑", "必爆", "包过", "一定会");
    private static final List<String> PROFESSIONAL_MARKERS = List.of(
            "用户", "场景", "结论", "动作", "风险", "下一步", "逻辑", "结构", "策略", "转化", "复盘");

    public ScriptReviewDto review(ScriptEntity script, List<AnalyzedFragment> fragments) {
        String content = effectiveContent(script);
        String normalized = content.toLowerCase(Locale.ROOT);
        Set<FragmentType> types = new LinkedHashSet<>();
        for (AnalyzedFragment fragment : fragments) {
            types.add(fragment.type());
        }

        int completenessScore = computeCompletenessScore(content, types);
        int gainScore = computeKeywordScore(normalized, GAIN_MARKERS, 45, 92,
                types.contains(FragmentType.VALUE) ? 18 : 0);
        int surpriseScore = computeKeywordScore(normalized, SURPRISE_MARKERS, 28, 88,
                types.contains(FragmentType.TWIST) ? 16 : 0);
        List<String> riskFlags = detectRiskFlags(content);
        int authenticityScore = clamp(82 - riskFlags.size() * 12
                + (content.length() > 80 ? 6 : 0)
                + (types.contains(FragmentType.VALUE) ? 4 : 0));
        int credibilityScore = clamp(80 - riskFlags.size() * 14
                + (containsAny(normalized, "数据", "案例", "结果", "风险", "下一步") ? 6 : 0));
        int professionalismScore = computeKeywordScore(normalized, PROFESSIONAL_MARKERS, 42, 90,
                content.length() > 120 ? 8 : 0);
        int interestingnessScore = clamp(40
                + (types.contains(FragmentType.HOOK) ? 18 : 0)
                + (types.contains(FragmentType.TWIST) ? 12 : 0)
                + (containsQuestionOrContrast(content) ? 10 : 0)
                + Math.min(10, countExclamationAndQuestion(content) * 2));

        boolean complete = completenessScore >= 72;
        int overallScore = clamp((int) Math.round(
                completenessScore * 0.20
                        + gainScore * 0.25
                        + surpriseScore * 0.15
                        + authenticityScore * 0.15
                        + professionalismScore * 0.10
                        + credibilityScore * 0.10
                        + interestingnessScore * 0.05));

        boolean featuredCandidate = complete
                && overallScore >= 80
                && gainScore >= 75
                && authenticityScore >= 70
                && credibilityScore >= 70
                && riskFlags.isEmpty();
        double featuredProbability = Math.max(0.05, Math.min(0.98, overallScore / 100.0));

        List<String> highlights = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        if (types.contains(FragmentType.HOOK)) {
            highlights.add("开场有明确钩子，具备前 3 秒留人的基础。");
        } else {
            issues.add("开场抓力偏弱，读者进入内容的理由不够明确。");
            suggestions.add("第一句先给结论、反差或问题，不要直接铺背景。");
        }
        if (gainScore >= 75) {
            highlights.add("信息增量明确，观众能快速获得方法、结论或模板。");
        } else {
            issues.add("获得感还不够强，方法和动作指令不够具体。");
            suggestions.add("补充可执行步骤、清单或模板句，提升获得感。");
        }
        if (surpriseScore >= 70) {
            highlights.add("具备反差或转折，能提升记忆点和完播潜力。");
        } else {
            issues.add("惊喜感一般，内容较顺，缺少反常识或转折。");
            suggestions.add("增加一句反常识判断或结果反转，制造惊喜感。");
        }
        if (!riskFlags.isEmpty()) {
            issues.add("存在高风险表达：" + String.join("、", riskFlags));
            suggestions.add("弱化绝对化、保证式或过度承诺表达，提升真实性和可信度。");
        }
        if (!complete) {
            issues.add("结构完整度不足，结尾收束或价值段不够稳。");
            suggestions.add("补全“钩子-价值-收束/行动”三段式结构。");
        }
        if (professionalismScore >= 75) {
            highlights.add("表达包含具体场景和动作，专业度较稳定。");
        }

        String featuredConclusion = featuredCandidate ? "具备精选候选特征" : "暂不属于稳定的精选候选";
        String featuredReason = featuredCandidate
                ? "结构完整、信息增量明确，且真实可信风险较低。"
                : buildReason(complete, gainScore, surpriseScore, riskFlags);
        String summary = "这条内容%s，完整度 %d，获得感 %d，惊喜感 %d，整体更偏%s。".formatted(
                featuredCandidate ? "具备精选候选基础" : "距离精选候选还有差距",
                completenessScore,
                gainScore,
                surpriseScore,
                featuredCandidate ? "优质内容候选" : "可优化内容");

        return new ScriptReviewDto(
                featuredCandidate,
                round(featuredProbability),
                featuredConclusion,
                featuredReason,
                complete,
                completenessScore,
                gainScore,
                surpriseScore,
                authenticityScore,
                professionalismScore,
                credibilityScore,
                interestingnessScore,
                overallScore,
                summary,
                deduplicate(highlights),
                deduplicate(issues),
                deduplicate(suggestions),
                riskFlags);
    }

    private int computeCompletenessScore(String content, Set<FragmentType> types) {
        int score = 24;
        if (types.contains(FragmentType.HOOK)) {
            score += 18;
        }
        if (types.contains(FragmentType.SETUP)) {
            score += 14;
        }
        if (types.contains(FragmentType.VALUE)) {
            score += 20;
        }
        if (types.contains(FragmentType.CTA) || types.contains(FragmentType.BODY)) {
            score += 12;
        }
        if (content.length() >= 90) {
            score += 8;
        }
        if (content.length() >= 160) {
            score += 6;
        }
        return clamp(score);
    }

    private int computeKeywordScore(String content, List<String> markers, int baseScore, int maxScore, int bonus) {
        int hits = 0;
        for (String marker : markers) {
            if (content.contains(marker.toLowerCase(Locale.ROOT))) {
                hits++;
            }
        }
        return clamp(Math.min(maxScore, baseScore + hits * 8 + bonus));
    }

    private List<String> detectRiskFlags(String content) {
        String normalized = content.toLowerCase(Locale.ROOT);
        List<String> flags = new ArrayList<>();
        for (String marker : RISK_MARKERS) {
            if (normalized.contains(marker.toLowerCase(Locale.ROOT))) {
                flags.add(marker);
            }
        }
        return deduplicate(flags);
    }

    private boolean containsQuestionOrContrast(String content) {
        return content.contains("？") || content.contains("?")
                || content.contains("不是")
                || content.contains("而是")
                || content.contains("但");
    }

    private boolean containsAny(String content, String... markers) {
        for (String marker : markers) {
            if (content.contains(marker.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private int countExclamationAndQuestion(String content) {
        int count = 0;
        for (char ch : content.toCharArray()) {
            if (ch == '！' || ch == '!' || ch == '？' || ch == '?') {
                count++;
            }
        }
        return count;
    }

    private String effectiveContent(ScriptEntity script) {
        if (StringUtils.hasText(script.getContent())) {
            return script.getContent();
        }
        return script.getTranscript() == null ? "" : script.getTranscript();
    }

    private String buildReason(boolean complete, int gainScore, int surpriseScore, List<String> riskFlags) {
        List<String> reasons = new ArrayList<>();
        if (!complete) {
            reasons.add("结构完整度不够");
        }
        if (gainScore < 75) {
            reasons.add("信息获得感不足");
        }
        if (surpriseScore < 70) {
            reasons.add("惊喜感偏弱");
        }
        if (!riskFlags.isEmpty()) {
            reasons.add("存在真实性或可信度风险");
        }
        return reasons.isEmpty() ? "整体质量尚可，但缺少足够强的精选信号。" : String.join("，", reasons) + "。";
    }

    private List<String> deduplicate(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
