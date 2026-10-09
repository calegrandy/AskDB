package io.github.calegrandy.askdb.model;

/**
 * One SQL query the model tried while answering a question.
 * Exactly one of {@code rowCount} (it ran) and {@code error} (it failed) is set.
 */
public record QueryAttempt(String sql, Integer rowCount, String error, long durationMs) {

    public static QueryAttempt succeeded(String sql, int rowCount, long durationMs) {
        return new QueryAttempt(sql, rowCount, null, durationMs);
    }

    public static QueryAttempt failed(String sql, String error, long durationMs) {
        return new QueryAttempt(sql, null, error, durationMs);
    }
}
