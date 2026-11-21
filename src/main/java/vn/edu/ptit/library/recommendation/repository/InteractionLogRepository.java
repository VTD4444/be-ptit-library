package vn.edu.ptit.library.recommendation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.recommendation.entity.InteractionLog;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InteractionLogRepository extends JpaRepository<InteractionLog, UUID> {

    boolean existsByUserIdAndDocumentIdAndEventTypeAndCreatedAtAfter(
            UUID userId,
            UUID documentId,
            String eventType,
            OffsetDateTime after
    );

    Optional<InteractionLog> findFirstByUserIdAndDocumentIdAndEventTypeOrderByCreatedAtDesc(
            UUID userId,
            UUID documentId,
            String eventType
    );

    @Modifying
    void deleteByUserIdAndDocumentIdAndEventType(UUID userId, UUID documentId, String eventType);

    @Query("SELECT DISTINCT il.document.id FROM InteractionLog il WHERE il.user.id = :userId AND il.implicitWeight >= :minWeight")
    List<UUID> findPositiveDocumentIdsByUserId(@Param("userId") UUID userId, @Param("minWeight") double minWeight);
}
