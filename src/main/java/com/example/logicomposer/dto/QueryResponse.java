package com.example.logicomposer.dto;

import java.util.List;
import java.util.Map;

/**
 * The answer to a question, plus the last successful SQL query and its rows,
 * so callers can check how the answer was reached.
 */
public record QueryResponse(String answer, String sql, List<Map<String, Object>> rows) {}
