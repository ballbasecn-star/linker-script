package com.linkscript.core.score;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class HeatScoreCalculator {

    private static final double WEIGHT_LIKES = 0.4;
    private static final double WEIGHT_SHARES = 0.3;
    private static final double WEIGHT_COMMENTS = 0.2;
    private static final double WEIGHT_VIEWS = 0.1;
    private static final double NORM_CAP = 10000.0;

    public HeatResult calculate(Map<String, Object> statistics) {
        if (statistics == null || statistics.isEmpty()) {
            return new HeatResult(0, "D");
        }

        double likes = normalize(extractNumber(statistics, "likes", "digg_count", "like_count"));
        double shares = normalize(extractNumber(statistics, "shares", "share_count", "forward_count"));
        double comments = normalize(extractNumber(statistics, "comments", "comment_count"));
        double views = normalize(extractNumber(statistics, "views", "play_count", "view_count", "read_count"));

        double score = WEIGHT_LIKES * likes
                + WEIGHT_SHARES * shares
                + WEIGHT_COMMENTS * comments
                + WEIGHT_VIEWS * views;

        score = Math.round(score * 100.0) / 100.0;
        String level = resolveLevel(score);
        return new HeatResult(score, level);
    }

    double normalize(double value) {
        return Math.min(value / NORM_CAP, 1.0);
    }

    String resolveLevel(double score) {
        if (score >= 0.8)
            return "S";
        if (score >= 0.6)
            return "A";
        if (score >= 0.4)
            return "B";
        if (score >= 0.2)
            return "C";
        return "D";
    }

    private double extractNumber(Map<String, Object> stats, String... keys) {
        for (String key : keys) {
            Object value = stats.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value instanceof String str) {
                try {
                    return Double.parseDouble(str.replace(",", "").trim());
                } catch (NumberFormatException ignored) {
                    // try next key
                }
            }
        }
        return 0;
    }

    public record HeatResult(double score, String level) {
    }
}
