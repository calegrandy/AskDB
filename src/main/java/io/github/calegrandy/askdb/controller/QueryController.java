package io.github.calegrandy.askdb.controller;

import io.github.calegrandy.askdb.dto.QueryRequest;
import io.github.calegrandy.askdb.dto.QueryResponse;
import io.github.calegrandy.askdb.query.DatabaseQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/connections/{id}/queries")
public class QueryController {

    private final DatabaseQueryService databaseQueryService;

    public QueryController(DatabaseQueryService databaseQueryService) {
        this.databaseQueryService = databaseQueryService;
    }

    @PostMapping
    public QueryResponse ask(@PathVariable Long id, @RequestBody @Valid QueryRequest request) {
        return databaseQueryService.ask(id, request.question());
    }
}
