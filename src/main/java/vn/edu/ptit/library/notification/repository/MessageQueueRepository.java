package vn.edu.ptit.library.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.notification.entity.MessageQueue;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface MessageQueueRepository extends JpaRepository<MessageQueue, Long> {

    Page<MessageQueue> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<MessageQueue> findByStatusAndScheduledAtBefore(String status, OffsetDateTime scheduledAt);
}
