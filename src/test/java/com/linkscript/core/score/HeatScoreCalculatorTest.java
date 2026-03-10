package com.linkscript.core.score;

import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HeatScoreCalculatorTest {

    private final HeatScoreCalculator calculator = new HeatScoreCalculator();

    @Test
    void shouldReturnDWhenStatsNull() {
        HeatScoreCalculator.HeatResult result = calculator.calculate(null);
        assertThat(result.score()).isEqualTo(0);
        assertThat(result.level()).isEqualTo("D");
    }

    @Test
    void shouldReturnDWhenStatsEmpty() {
        HeatScoreCalculator.HeatResult result = calculator.calculate(Map.of());
        assertThat(result.score()).isEqualTo(0);
        assertThat(result.level()).isEqualTo("D");
    }

    @Test
    void shouldCalculateLevelD() {
        HeatScoreCalculator.HeatResult result = calculator.calculate(Map.of(
                "likes", 100,
                "shares", 10,
                "comments", 50,
                "views", 500));
        assertThat(result.level()).isEqualTo("D");
        assertThat(result.score()).isLessThan(0.2);
    }

    @Test
    void shouldCalculateLevelS() {
        HeatScoreCalculator.HeatResult result = calculator.calculate(Map.of(
                "likes", 50000,
                "shares", 20000,
                "comments", 15000,
                "views", 100000));
        assertThat(result.level()).isEqualTo("S");
        assertThat(result.score()).isGreaterThanOrEqualTo(0.8);
    }

    @Test
    void shouldHandleAlternativeKeyNames() {
        HeatScoreCalculator.HeatResult result = calculator.calculate(Map.of(
                "digg_count", 10000,
                "share_count", 5000,
                "comment_count", 3000,
                "play_count", 80000));
        assertThat(result.score()).isGreaterThan(0.0);
        assertThat(result.level()).isNotNull();
    }

    @Test
    void shouldHandleStringNumbers() {
        HeatScoreCalculator.HeatResult result = calculator.calculate(Map.of(
                "likes", "5000",
                "shares", "2,000",
                "comments", "1000",
                "views", "30000"));
        assertThat(result.score()).isGreaterThan(0.0);
    }

    @Test
    void shouldCapNormalizationAt1() {
        assertThat(calculator.normalize(20000)).isEqualTo(1.0);
        assertThat(calculator.normalize(10000)).isEqualTo(1.0);
        assertThat(calculator.normalize(5000)).isEqualTo(0.5);
    }

    @Test
    void shouldResolveLevelsCorrectly() {
        assertThat(calculator.resolveLevel(0.85)).isEqualTo("S");
        assertThat(calculator.resolveLevel(0.8)).isEqualTo("S");
        assertThat(calculator.resolveLevel(0.7)).isEqualTo("A");
        assertThat(calculator.resolveLevel(0.6)).isEqualTo("A");
        assertThat(calculator.resolveLevel(0.5)).isEqualTo("B");
        assertThat(calculator.resolveLevel(0.4)).isEqualTo("B");
        assertThat(calculator.resolveLevel(0.3)).isEqualTo("C");
        assertThat(calculator.resolveLevel(0.2)).isEqualTo("C");
        assertThat(calculator.resolveLevel(0.1)).isEqualTo("D");
        assertThat(calculator.resolveLevel(0.0)).isEqualTo("D");
    }
}
