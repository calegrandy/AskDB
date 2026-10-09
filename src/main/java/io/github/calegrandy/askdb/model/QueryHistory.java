package io.github.calegrandy.askdb.model;

import io.github.calegrandy.askdb.enums.QueryStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A record of one question asked of a connection and what happened. Result rows are deliberately
 * not stored: they're copies of data from someone else's database.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "query_history")
public class QueryHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long connectionId;
    private String question;
    @Enumerated(EnumType.STRING)
    private QueryStatus status;
    private String answer;
    private String finalSql;
    private Integer rowCount;
    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    private List<QueryAttempt> attempts = new ArrayList<>();
    private String errorMessage;
    private String model;
    private Integer inputTokens;
    private Integer outputTokens;
    private long durationMs;
    private Instant createdAt;
}
