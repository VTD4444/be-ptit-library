package vn.edu.ptit.library.recommendation.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.edu.ptit.library.recommendation.service.InteractionLogService;

@Component
@RequiredArgsConstructor
public class InteractionLogEventListener {

    private final InteractionLogService interactionLogService;

    @EventListener
    public void onSyncInteraction(InteractionRecordedEvent.Sync event) {
        interactionLogService.recordSyncEvent(event);
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAsyncReadInteraction(InteractionRecordedEvent.AsyncRead event) {
        interactionLogService.recordReadInteractionAsync(event);
    }
}
