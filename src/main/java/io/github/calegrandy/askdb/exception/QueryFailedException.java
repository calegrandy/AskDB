package io.github.calegrandy.askdb.exception;

/**
 * Answering a question failed, for example because the model API or the target database was unavailable.
 * The failure has already been saved to the query history under {@code queryId}.
 */
public class QueryFailedException extends RuntimeException {

    private final Long queryId;

    public QueryFailedException(Long queryId, Throwable cause) {
        super("Query " + queryId + " failed: " + cause.getMessage(), cause);
        this.queryId = queryId;
    }

    public Long getQueryId() {
        return queryId;
    }
}
