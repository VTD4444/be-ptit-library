package vn.edu.ptit.library.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.notification.entity.MessageQueue;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageQueueRepository extends JpaRepository<MessageQueue, Long> {

    Page<MessageQueue> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<MessageQueue> findByIdAndUserId(Long id, UUID userId);

    @Query("SELECT COUNT(m) FROM MessageQueue m WHERE m.user.id = :userId AND UPPER(COALESCE(m.status, '')) <> :readStatus")
    long countUnreadByUserId(@Param("userId") UUID userId, @Param("readStatus") String readStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MessageQueue m SET m.status = :readStatus WHERE m.user.id = :userId AND UPPER(COALESCE(m.status, '')) <> :readStatus")
    int markAllAsReadByUserId(@Param("userId") UUID userId, @Param("readStatus") String readStatus);

    List<MessageQueue> findByStatusAndScheduledAtBefore(String status, OffsetDateTime scheduledAt);

    @Query(
            value = """
                    SELECT m FROM MessageQueue m
                    JOIN FETCH m.user u
                    WHERE (:keywordPattern = ''
                           OR LOWER(COALESCE(m.content, '')) LIKE :keywordPattern
                           OR LOWER(COALESCE(u.fullName, '')) LIKE :keywordPattern
                           OR LOWER(COALESCE(u.userCode, '')) LIKE :keywordPattern
                           OR LOWER(COALESCE(u.email, '')) LIKE :keywordPattern)
                      AND (:messageType = '' OR UPPER(COALESCE(m.messageType, '')) = :messageType)
                      AND (:status = '' OR UPPER(COALESCE(m.status, '')) = :status)
                    ORDER BY m.createdAt DESC, m.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(m) FROM MessageQueue m
                    JOIN m.user u
                    WHERE (:keywordPattern = ''
                           OR LOWER(COALESCE(m.content, '')) LIKE :keywordPattern
                           OR LOWER(COALESCE(u.fullName, '')) LIKE :keywordPattern
                           OR LOWER(COALESCE(u.userCode, '')) LIKE :keywordPattern
                           OR LOWER(COALESCE(u.email, '')) LIKE :keywordPattern)
                      AND (:messageType = '' OR UPPER(COALESCE(m.messageType, '')) = :messageType)
                      AND (:status = '' OR UPPER(COALESCE(m.status, '')) = :status)
                    """
    )
    Page<MessageQueue> searchAdminMessages(
            @Param("keywordPattern") String keywordPattern,
            @Param("messageType") String messageType,
            @Param("status") String status,
            Pageable pageable);
}
