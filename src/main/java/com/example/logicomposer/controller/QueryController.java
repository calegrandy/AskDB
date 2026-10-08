package com.example.logicomposer.controller;

import com.example.logicomposer.dto.QueryRequest;
import com.example.logicomposer.dto.QueryResponse;
import com.example.logicomposer.query.DatabaseQueryService;
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
