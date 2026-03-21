package com.linkscript.core.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.linkscript.domain.dto.ScriptReviewDto;
import com.linkscript.domain.entity.FragmentType;
import com.linkscript.domain.entity.ScriptEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

class FallbackContentReviewerTest {

    private final FallbackContentReviewer reviewer = new FallbackContentReviewer();

    @Test
    void shouldMarkStrongStructuredContentAsFeaturedCandidate() {
        ScriptEntity script = new ScriptEntity();
        script.setTitle("汇报项目别再先讲背景");
        script.setContent("老板最想先听到的不是过程，而是结果。你先给结论，再讲动作，最后补风险和下一步。"
                + "如果你记不住，就直接套这个三步模板。");

        List<AnalyzedFragment> fragments = List.of(
                new AnalyzedFragment(FragmentType.HOOK, "老板最想先听到的不是过程，而是结果。", "反差开场", 0.9),
                new AnalyzedFragment(FragmentType.SETUP, "很多人汇报时一开口就讲背景。", "铺垫问题", 0.8),
                new AnalyzedFragment(FragmentType.VALUE, "你先给结论，再讲动作，最后补风险和下一步。", "交付模板", 0.9),
                new AnalyzedFragment(FragmentType.CTA, "直接套这个三步模板。", "收束动作", 0.8));

        ScriptReviewDto review = reviewer.review(script, fragments);

        assertThat(review.complete()).isTrue();
        assertThat(review.gainScore()).isGreaterThanOrEqualTo(75);
        assertThat(review.overallScore()).isGreaterThanOrEqualTo(75);
        assertThat(review.highlights()).isNotEmpty();
    }

    @Test
    void shouldDetectRiskyAbsoluteClaims() {
        ScriptEntity script = new ScriptEntity();
        script.setTitle("这个方法保证必爆");
        script.setContent("只要照着做就保证必爆，100%有效，立刻涨粉。");

        List<AnalyzedFragment> fragments = List.of(
                new AnalyzedFragment(FragmentType.HOOK, "只要照着做就保证必爆。", "夸张承诺", 0.5));

        ScriptReviewDto review = reviewer.review(script, fragments);

        assertThat(review.riskFlags()).isNotEmpty();
        assertThat(review.authenticityScore()).isLessThan(70);
        assertThat(review.featuredCandidate()).isFalse();
    }
}
