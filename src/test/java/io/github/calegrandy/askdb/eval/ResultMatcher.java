package io.github.calegrandy.askdb.eval;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The free, deterministic grader: did AskDB's final query return the reference values?
 *
 * <p>Every value in the reference result must appear somewhere in AskDB's result. Column names, column order,
 * row order and extra columns don't matter, so a correct query written differently still matches. Numbers are
 * compared to 2 decimal places and text ignores case.
 */
public final class ResultMatcher {

    private ResultMatcher() {
    }

    public static boolean matches(List<Map<String, Object>> reference, List<Map<String, Object>> actual) {
        if (reference.isEmpty()) {
            return actual.isEmpty();
        }
        Set<String> actualValues = values(actual);
        return actualValues.containsAll(values(reference));
    }

    private static Set<String> values(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(Map::values)
                .flatMap(Collection::stream)
                .map(ResultMatcher::normalize)
                .collect(Collectors.toSet());
    }

    static String normalize(Object value) {
        return switch (value) {
            case null -> "null";
            case Number n -> new BigDecimal(n.toString()).setScale(2, RoundingMode.HALF_UP).toPlainString();
            default -> value.toString().trim().toLowerCase();
        };
    }
}
