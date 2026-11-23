package vn.edu.ptit.library.circulation.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.circulation.entity.HoldQueue;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HoldQueueRepository extends JpaRepository<HoldQueue, Long> {

    List<HoldQueue> findByDocumentIdAndStatusOrderByQueuePositionAsc(UUID documentId, String status);

    Optional<HoldQueue> findFirstByDocumentIdAndStatusOrderByQueuePositionAsc(UUID documentId, String status);

    Optional<HoldQueue> findByUserIdAndDocumentIdAndStatus(UUID userId, UUID documentId, String status);

    @Query("SELECT h FROM HoldQueue h WHERE h.user.id = :userId AND h.document.id = :documentId AND h.status IN ('WAITING', 'READY_FOR_PICKUP')")
    Optional<HoldQueue> findActiveHoldByUserAndDocument(@Param("userId") UUID userId, @Param("documentId") UUID documentId);

    Optional<HoldQueue> findByPhysicalCopyIdAndStatus(UUID physicalCopyId, String status);

    Page<HoldQueue> findByUserId(UUID userId, Pageable pageable);

    @Query("SELECT h FROM HoldQueue h JOIN FETCH h.document LEFT JOIN FETCH h.physicalCopy "
            + "WHERE h.user.id = :userId ORDER BY h.requestDate DESC, h.id DESC")
    List<HoldQueue> findAllByUserIdWithDetails(@Param("userId") UUID userId);

    @Query("SELECT h FROM HoldQueue h JOIN FETCH h.document d JOIN FETCH h.user u LEFT JOIN FETCH h.physicalCopy "
            + "WHERE h.status IN :statuses AND (LOWER(d.title) LIKE :keyword OR LOWER(u.userCode) LIKE :keyword "
            + "OR LOWER(u.fullName) LIKE :keyword OR LOWER(COALESCE(u.email, '')) LIKE :keyword) "
            + "ORDER BY d.title ASC, h.queuePosition ASC NULLS FIRST, h.id ASC")
    List<HoldQueue> searchForStaff(@Param("statuses") Collection<String> statuses,
                                   @Param("keyword") String keyword,
                                   Pageable pageable);

    @Query("SELECT h FROM HoldQueue h WHERE h.status = 'READY_FOR_PICKUP' AND h.pickupDeadline < :now ORDER BY h.pickupDeadline ASC")
    List<HoldQueue> findReadyHoldsPastPickupDeadline(@Param("now") OffsetDateTime now);

    long countByDocumentIdAndStatus(UUID documentId, String status);

    long countByStatusIn(Collection<String> statuses);

    @Query("SELECT COALESCE(MAX(h.queuePosition), 0) + 1 FROM HoldQueue h WHERE h.document.id = :documentId AND h.status = 'WAITING'")
    int getNextQueuePosition(@Param("documentId") UUID documentId);

    @Modifying
    @Query("UPDATE HoldQueue h SET h.queuePosition = h.queuePosition - 1 WHERE h.document.id = :documentId AND h.status = 'WAITING' AND h.queuePosition > :cancelledPosition")
    int decrementQueuePositionsAfter(@Param("documentId") UUID documentId, @Param("cancelledPosition") Integer cancelledPosition);
}
