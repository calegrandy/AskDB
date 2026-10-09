package io.github.calegrandy.askdb.controller;

import io.github.calegrandy.askdb.dto.QueryHistoryResponse;
import io.github.calegrandy.askdb.service.QueryHistoryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/queries")
public class QueryHistoryController {

    private final QueryHistoryService queryHistoryService;

    public QueryHistoryController(QueryHistoryService queryHistoryService) {
        this.queryHistoryService = queryHistoryService;
    }

    // Also works after the query's connection was deleted (connectionId is then null).
    @GetMapping("/{id}")
    public QueryHistoryResponse getQuery(@PathVariable Long id) {
        return QueryHistoryResponse.from(queryHistoryService.getQuery(id));
    }
}
