package vn.edu.ptit.library.indexing.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.indexing.entity.BackgroundJob;

import java.util.List;
import java.util.UUID;

@Repository
public interface BackgroundJobRepository extends JpaRepository<BackgroundJob, Long> {

    List<BackgroundJob> findByJobTypeAndStatus(String jobType, String status);

    Page<BackgroundJob> findByStatus(String status, Pageable pageable);

    List<BackgroundJob> findByTargetId(UUID targetId);
}
