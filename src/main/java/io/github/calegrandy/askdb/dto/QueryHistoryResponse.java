package io.github.calegrandy.askdb.dto;

import io.github.calegrandy.askdb.enums.QueryStatus;
import io.github.calegrandy.askdb.model.QueryAttempt;
import io.github.calegrandy.askdb.model.QueryHistory;

import java.time.Instant;
import java.util.List;

public record QueryHistoryResponse(
        Long id,
        Long connectionId,
        String question,
        QueryStatus status,
        String answer,
        String finalSql,
        Integer rowCount,
        List<QueryAttempt> attempts,
        String errorMessage,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        long durationMs,
        Instant createdAt
) {

    public static QueryHistoryResponse from(QueryHistory history) {
        return new QueryHistoryResponse(
                history.getId(),
                history.getConnectionId(),
                history.getQuestion(),
                history.getStatus(),
                history.getAnswer(),
                history.getFinalSql(),
                history.getRowCount(),
                history.getAttempts(),
                history.getErrorMessage(),
                history.getModel(),
                history.getInputTokens(),
                history.getOutputTokens(),
                history.getDurationMs(),
                history.getCreatedAt()
        );
    }
}
