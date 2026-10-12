package io.github.calegrandy.askdb.eval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResultMatcherTest {

    private static final List<Map<String, Object>> REFERENCE = List.of(Map.of("title", "BUCKET BROTHERHOOD", "rentals", 34));

    @Test
    void matchesSameValuesUnderDifferentColumnNamesWithExtraColumns() {
        assertThat(ResultMatcher.matches(REFERENCE, List.of(
                Map.of("film", "Bucket Brotherhood", "times_rented", 34L, "film_id", 103)))).isTrue();
    }

    @Test
    void comparesNumbersToTwoDecimals() {
        assertThat(ResultMatcher.matches(List.of(Map.of("avg", 5.02533)), List.of(Map.of("avg_days", 5.03)))).isTrue();
        assertThat(ResultMatcher.matches(List.of(Map.of("total", 11413.86)), List.of(Map.of("sum", 11413.9)))).isFalse();
    }

    @Test
    void failsWhenAReferenceValueIsMissing() {
        assertThat(ResultMatcher.matches(REFERENCE, List.of(Map.of("title", "BUCKET BROTHERHOOD", "rentals", 33)))).isFalse();
    }

    @Test
    void failsOnEmptyResult() {
        assertThat(ResultMatcher.matches(REFERENCE, List.of())).isFalse();
    }
}
