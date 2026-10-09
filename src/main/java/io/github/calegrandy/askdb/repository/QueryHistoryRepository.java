package io.github.calegrandy.askdb.repository;

import io.github.calegrandy.askdb.model.QueryHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QueryHistoryRepository extends JpaRepository<QueryHistory, Long> {

    // Newest first; id breaks ties between entries created in the same instant.
    Page<QueryHistory> findByConnectionIdOrderByCreatedAtDescIdDesc(Long connectionId, Pageable pageable);
}
