package vn.edu.ptit.library.search.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class SearchConfigActivationLock {
    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire() {
        // PostgreSQL transaction-scoped lock: also excludes external INSERT/UPDATE/DELETE
        // while permitting ordinary SELECT. All rows are read only AFTER this lock.
        jdbcTemplate.execute("LOCK TABLE public.search_vector_configs IN EXCLUSIVE MODE");
    }
}
