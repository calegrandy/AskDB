package io.github.calegrandy.askdb.dto;

import java.util.List;
import java.util.Map;

/**
 * The answer to a question, plus the last successful SQL query and its rows,
 * so callers can check how the answer was reached. {@code queryId} identifies the query history entry.
 */
public record QueryResponse(Long queryId, String answer, String sql, List<Map<String, Object>> rows) {}
