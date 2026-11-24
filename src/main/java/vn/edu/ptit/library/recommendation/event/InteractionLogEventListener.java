package vn.edu.ptit.library.recommendation.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.edu.ptit.library.recommendation.service.InteractionLogService;

@Slf4j
@Component
@RequiredArgsConstructor
public class InteractionLogEventListener {

    private final InteractionLogService interactionLogService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSyncInteraction(InteractionRecordedEvent.Sync event) {
        try {
            interactionLogService.recordSyncEvent(event);
        } catch (Exception ex) {
            log.warn("Khong ghi duoc sync interaction log {} cho user={} doc={}: {}",
                    event != null ? event.eventType() : null,
                    event != null ? event.userId() : null,
                    event != null ? event.documentId() : null,
                    ex.getMessage());
        }
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAsyncReadInteraction(InteractionRecordedEvent.AsyncRead event) {
        try {
            interactionLogService.recordReadInteractionAsync(event);
        } catch (Exception ex) {
            log.warn("Khong ghi duoc async interaction log {} cho user={} doc={}: {}",
                    event != null ? event.eventType() : null,
                    event != null ? event.userId() : null,
                    event != null ? event.documentId() : null,
                    ex.getMessage());
        }
    }
}
