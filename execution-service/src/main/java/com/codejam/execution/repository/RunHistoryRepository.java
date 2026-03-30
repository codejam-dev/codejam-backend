package com.codejam.execution.repository;

import com.codejam.execution.model.RunHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RunHistoryRepository extends JpaRepository<RunHistory, Long> {

    Page<RunHistory> findByUserId(String userId, Pageable pageable);

    @Modifying
    @Query(value = """
        DELETE FROM execution.run_history
        WHERE user_id = :userId
        AND id IN (
            SELECT id FROM execution.run_history
            WHERE user_id = :userId
            ORDER BY created_at DESC
            OFFSET :maxRuns
        )
        """, nativeQuery = true)
    void pruneOldRuns(@Param("userId") String userId,
                      @Param("maxRuns") int maxRuns);
}