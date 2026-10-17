package vn.edu.ptit.library.notification.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.library.circulation.entity.Circulation;
import vn.edu.ptit.library.circulation.entity.HoldQueue;
import vn.edu.ptit.library.circulation.repository.CirculationRepository;
import vn.edu.ptit.library.circulation.repository.HoldQueueRepository;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;
import vn.edu.ptit.library.common.enums.MessageStatus;
import vn.edu.ptit.library.common.util.SecurityUtils;
import vn.edu.ptit.library.notification.entity.MessageQueue;
import vn.edu.ptit.library.notification.repository.MessageQueueRepository;
import vn.edu.ptit.library.notification.service.NotificationService;
import vn.edu.ptit.library.user.entity.User;
import vn.edu.ptit.library.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final MessageQueueRepository messageQueueRepository;
    private final UserRepository userRepository;
    private final CirculationRepository circulationRepository;
    private final HoldQueueRepository holdQueueRepository;

    @Override
    @Transactional
    public MessageQueue queueNotification(UUID userId, Long circulationId, Long holdId, String channel, String messageType, String content) {
        User user = userRepository.getReferenceById(userId);
        Circulation circ = circulationId != null ? circulationRepository.getReferenceById(circulationId) : null;
        HoldQueue hold = holdId != null ? holdQueueRepository.getReferenceById(holdId) : null;

        MessageQueue message = MessageQueue.builder()
                .user(user)
                .circulation(circ)
                .hold(hold)
                .channel(channel != null ? channel : "IN_APP")
                .messageType(messageType)
                .content(content)
                .scheduledAt(OffsetDateTime.now())
                .status(MessageStatus.PENDING.name())
                .retryCount(0)
                .build();

        return messageQueueRepository.save(message);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MessageQueue> getMyNotifications(PaginationRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserIdOrThrow();
        Page<MessageQueue> page = messageQueueRepository.findByUserIdOrderByCreatedAtDesc(currentUserId, request.toPageable());
        return PageResponse.from(page);
    }

    @Override
    @Scheduled(cron = "0 0 8 * * *") // 8:00 AM hang ngay
    @Transactional
    public void scanOverdueLoansAndNotify() {
        log.info("Bat dau quét cac luot muon qua han de gui thong bao...");
        List<Circulation> overdueLoans = circulationRepository.findOverdueLoans(OffsetDateTime.now());

        for (Circulation loan : overdueLoans) {
            String content = String.format("Sach '%s' cua ban da qua han tra vao ngay %s. Vui long tra sach hoac lien he thu vien.",
                    loan.getPhysicalCopy().getDocument().getTitle(),
                    loan.getDueDate().toLocalDate());

            queueNotification(
                    loan.getUser().getId(),
                    loan.getId(),
                    null,
                    "IN_APP",
                    "LOAN_OVERDUE",
                    content
            );
        }
        log.info("Da tao {} thong bao qua han cho ban doc.", overdueLoans.size());
    }
}
