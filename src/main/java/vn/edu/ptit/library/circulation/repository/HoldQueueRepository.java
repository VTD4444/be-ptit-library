package vn.edu.ptit.library.circulation.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.circulation.entity.HoldQueue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HoldQueueRepository extends JpaRepository<HoldQueue, Long> {

    List<HoldQueue> findByDocumentIdAndStatusOrderByQueuePositionAsc(UUID documentId, String status);

    Optional<HoldQueue> findByUserIdAndDocumentIdAndStatus(UUID userId, UUID documentId, String status);

    Page<HoldQueue> findByUserId(UUID userId, Pageable pageable);

    @Query("SELECT COALESCE(MAX(h.queuePosition), 0) + 1 FROM HoldQueue h WHERE h.document.id = :documentId AND h.status = 'WAITING'")
    int getNextQueuePosition(@Param("documentId") UUID documentId);
}
