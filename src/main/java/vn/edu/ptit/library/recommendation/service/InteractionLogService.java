package vn.edu.ptit.library.recommendation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.library.catalog.repository.DocumentRepository;
import vn.edu.ptit.library.recommendation.entity.InteractionLog;
import vn.edu.ptit.library.recommendation.enums.InteractionEventType;
import vn.edu.ptit.library.recommendation.event.InteractionRecordedEvent;
import vn.edu.ptit.library.recommendation.repository.InteractionLogRepository;
import vn.edu.ptit.library.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionLogService {

    private static final int READ_DEDUP_MINUTES = 15;

    private final InteractionLogRepository interactionLogRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;

    @Transactional
    public void recordSyncEvent(InteractionRecordedEvent.Sync event) {
        if (event == null || event.userId() == null || event.documentId() == null) {
            return;
        }
        switch (event.eventType()) {
            case FAVORITE -> handleFavorite(event.userId(), event.documentId(), event.removeFavorite());
            case RATE -> handleRating(event.userId(), event.documentId(), event.ratingStars());
            case BORROW -> saveLog(event.userId(), event.documentId(), InteractionEventType.BORROW, null,
                    InteractionEventType.BORROW.getDefaultWeight());
            default -> log.debug("Bo qua sync interaction khong hop le: {}", event.eventType());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordReadInteractionAsync(InteractionRecordedEvent.AsyncRead event) {
        if (event == null || event.userId() == null || event.documentId() == null || !event.eventType().isAsyncReadEvent()) {
            return;
        }
        try {
            OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(READ_DEDUP_MINUTES);
            boolean recentlyLogged = interactionLogRepository
                    .existsByUserIdAndDocumentIdAndEventTypeAndCreatedAtAfter(
                            event.userId(), event.documentId(), event.eventType().name(), cutoff);
            if (recentlyLogged) {
                return;
            }
            saveLog(event.userId(), event.documentId(), event.eventType(), null,
                    event.eventType().getDefaultWeight());
        } catch (Exception ex) {
            log.warn("Khong ghi duoc interaction log {} cho user={} doc={}: {}",
                    event.eventType(), event.userId(), event.documentId(), ex.getMessage());
        }
    }

    private void handleFavorite(UUID userId, UUID documentId, boolean removeFavorite) {
        String eventName = InteractionEventType.FAVORITE.name();
        if (removeFavorite) {
            interactionLogRepository.deleteByUserIdAndDocumentIdAndEventType(userId, documentId, eventName);
            return;
        }
        if (interactionLogRepository
                .findFirstByUserIdAndDocumentIdAndEventTypeOrderByCreatedAtDesc(userId, documentId, eventName)
                .isEmpty()) {
            saveLog(userId, documentId, InteractionEventType.FAVORITE, null,
                    InteractionEventType.FAVORITE.getDefaultWeight());
        }
    }

    private void handleRating(UUID userId, UUID documentId, Integer ratingStars) {
        if (ratingStars == null) {
            return;
        }
        String eventName = InteractionEventType.RATE.name();
        double weight = ratingStars.doubleValue();
        InteractionLog existing = interactionLogRepository
                .findFirstByUserIdAndDocumentIdAndEventTypeOrderByCreatedAtDesc(userId, documentId, eventName)
                .orElse(null);
        if (existing != null) {
            existing.setRatingStars(ratingStars);
            existing.setImplicitWeight(weight);
            interactionLogRepository.save(existing);
            return;
        }
        saveLog(userId, documentId, InteractionEventType.RATE, ratingStars, weight);
    }

    private void saveLog(UUID userId, UUID documentId, InteractionEventType type, Integer ratingStars, double weight) {
        InteractionLog entry = InteractionLog.builder()
                .user(userRepository.getReferenceById(userId))
                .document(documentRepository.getReferenceById(documentId))
                .eventType(type.name())
                .ratingStars(ratingStars)
                .implicitWeight(weight)
                .build();
        interactionLogRepository.save(entry);
    }
}
