package io.github.calegrandy.askdb.controller;

import io.github.calegrandy.askdb.dto.QueryHistoryResponse;
import io.github.calegrandy.askdb.dto.QueryRequest;
import io.github.calegrandy.askdb.dto.QueryResponse;
import io.github.calegrandy.askdb.query.DatabaseQueryService;
import io.github.calegrandy.askdb.service.QueryHistoryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/connections/{id}/queries")
public class QueryController {

    private final DatabaseQueryService databaseQueryService;
    private final QueryHistoryService queryHistoryService;

    public QueryController(DatabaseQueryService databaseQueryService, QueryHistoryService queryHistoryService) {
        this.databaseQueryService = databaseQueryService;
        this.queryHistoryService = queryHistoryService;
    }

    @PostMapping
    public QueryResponse ask(@PathVariable Long id, @RequestBody @Valid QueryRequest request) {
        return databaseQueryService.ask(id, request.question());
    }

    // GET /api/connections/{id}/queries?page=0&size=20 -> this connection's query history, newest first
    @GetMapping
    public PagedModel<QueryHistoryResponse> getHistory(@PathVariable Long id, @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(queryHistoryService.getHistory(id, pageable).map(QueryHistoryResponse::from));
    }
}
