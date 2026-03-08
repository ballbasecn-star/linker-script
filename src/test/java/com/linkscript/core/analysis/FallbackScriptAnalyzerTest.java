package com.linkscript.core.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.linkscript.domain.entity.FragmentType;
import java.util.List;
import org.junit.jupiter.api.Test;

class FallbackScriptAnalyzerTest {

    private final FallbackScriptAnalyzer analyzer = new FallbackScriptAnalyzer();

    @Test
    void shouldSplitScriptIntoHookAndCtaFragments() {
        String content = "别再硬讲卖点了，这样拍用户才会停下来。"
                + "很多人一开口就在介绍功能，观众根本不买账。"
                + "其实你只要先抛冲突，再给解决方案，完播率就会上来。"
                + "想要模版，评论区扣 1。";

        List<AnalyzedFragment> fragments = analyzer.analyze("标题", content);

        assertThat(fragments).extracting(AnalyzedFragment::type)
                .contains(FragmentType.HOOK, FragmentType.SETUP, FragmentType.TWIST, FragmentType.CTA);
        assertThat(fragments.getFirst().content()).contains("别再硬讲卖点");
    }
}
