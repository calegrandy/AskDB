package io.github.calegrandy.askdb.service;

import io.github.calegrandy.askdb.exception.QueryNotFoundException;
import io.github.calegrandy.askdb.model.QueryHistory;
import io.github.calegrandy.askdb.repository.QueryHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class QueryHistoryService {

    private final QueryHistoryRepository queryHistoryRepository;
    private final ConnectionService connectionService;

    public QueryHistoryService(QueryHistoryRepository queryHistoryRepository, ConnectionService connectionService) {
        this.queryHistoryRepository = queryHistoryRepository;
        this.connectionService = connectionService;
    }

    public Page<QueryHistory> getHistory(Long connectionId, Pageable pageable) {
        connectionService.getConnection(connectionId);  // 404 for an unknown connection
        return queryHistoryRepository.findByConnectionIdOrderByCreatedAtDescIdDesc(connectionId, pageable);
    }

    public QueryHistory getQuery(Long id) {
        return queryHistoryRepository.findById(id)
                .orElseThrow(() -> new QueryNotFoundException("No query found with id: " + id));
    }
}
