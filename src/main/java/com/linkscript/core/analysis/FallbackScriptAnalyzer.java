package com.linkscript.core.analysis;

import com.linkscript.domain.entity.FragmentType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FallbackScriptAnalyzer {

    private static final List<String> CTA_MARKERS = List.of("点赞", "关注", "收藏", "评论", "私信", "点击", "立即", "现在");
    private static final List<String> TWIST_MARKERS = List.of("但是", "其实", "结果", "反转", "没想到", "不过");

    public List<AnalyzedFragment> analyze(String title, String content) {
        String source = StringUtils.hasText(content) ? content : title;
        if (!StringUtils.hasText(source)) {
            return List.of();
        }

        List<String> sentences = Arrays.stream(source.replace("\r", "\n").split("(?<=[。！？!?；;\\n])"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        if (sentences.isEmpty()) {
            return List.of(new AnalyzedFragment(FragmentType.BODY, source.trim(), "整体内容较短，保留为正文片段。"));
        }

        List<AnalyzedFragment> fragments = new ArrayList<>();
        fragments.add(new AnalyzedFragment(FragmentType.HOOK, sentences.getFirst(), "开场直接抛出问题或结果，适合作为黄金 3 秒钩子。"));

        if (sentences.size() == 1) {
            return fragments;
        }

        String ctaSentence = findLastMatching(sentences, CTA_MARKERS);
        List<String> middle = new ArrayList<>(sentences.subList(1, sentences.size()));
        if (StringUtils.hasText(ctaSentence)) {
            middle.remove(ctaSentence);
        }

        String twistSentence = findFirstMatching(middle, TWIST_MARKERS);
        if (StringUtils.hasText(twistSentence)) {
            middle.remove(twistSentence);
        }

        if (!middle.isEmpty()) {
            fragments.add(new AnalyzedFragment(
                    FragmentType.SETUP,
                    middle.getFirst(),
                    "先铺设场景、痛点或冲突背景，承接钩子。"
            ));
        }

        if (middle.size() > 1) {
            String value = join(middle.subList(1, middle.size()));
            fragments.add(new AnalyzedFragment(
                    FragmentType.VALUE,
                    value,
                    "用于交付核心方法、观点或信息增量。"
            ));
        }

        if (StringUtils.hasText(twistSentence)) {
            fragments.add(new AnalyzedFragment(FragmentType.TWIST, twistSentence, "通过反转或出乎意料的信息提高完播与记忆点。"));
        }

        if (StringUtils.hasText(ctaSentence)) {
            fragments.add(new AnalyzedFragment(FragmentType.CTA, ctaSentence, "引导用户完成关注、评论、私信等动作。"));
        }

        if (fragments.size() == 1) {
            fragments.add(new AnalyzedFragment(FragmentType.BODY, join(sentences.subList(1, sentences.size())), "主体内容归纳为正文。"));
        }

        return fragments.stream()
                .filter(fragment -> StringUtils.hasText(fragment.content()))
                .collect(Collectors.toList());
    }

    private String findLastMatching(List<String> sentences, List<String> markers) {
        for (int i = sentences.size() - 1; i >= 0; i--) {
            String sentence = sentences.get(i);
            String lower = sentence.toLowerCase(Locale.ROOT);
            if (markers.stream().anyMatch(lower::contains)) {
                return sentence;
            }
        }
        return null;
    }

    private String findFirstMatching(List<String> sentences, List<String> markers) {
        for (String sentence : sentences) {
            String lower = sentence.toLowerCase(Locale.ROOT);
            if (markers.stream().anyMatch(lower::contains)) {
                return sentence;
            }
        }
        return null;
    }

    private String join(List<String> sentences) {
        return sentences.stream().filter(StringUtils::hasText).collect(Collectors.joining(" "));
    }
}
