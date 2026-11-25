package vn.edu.ptit.library.notification.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import vn.edu.ptit.library.common.enums.UserRole;
import vn.edu.ptit.library.common.enums.UserStatus;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;
import vn.edu.ptit.library.common.util.SecurityUtils;
import vn.edu.ptit.library.notification.dto.AdminNotificationItemDto;
import vn.edu.ptit.library.notification.dto.MarkNotificationsReadResponse;
import vn.edu.ptit.library.notification.dto.NotificationItemDto;
import vn.edu.ptit.library.notification.dto.SendNotificationRequest;
import vn.edu.ptit.library.notification.dto.SendNotificationResponse;
import vn.edu.ptit.library.notification.dto.UnreadNotificationCountDto;
import vn.edu.ptit.library.notification.entity.MessageQueue;
import vn.edu.ptit.library.notification.repository.MessageQueueRepository;
import vn.edu.ptit.library.notification.service.NotificationService;
import vn.edu.ptit.library.user.entity.User;
import vn.edu.ptit.library.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final String DEFAULT_CHANNEL = "IN_APP";
    private static final String DEFAULT_MESSAGE_TYPE = "SYSTEM_ANNOUNCEMENT";
    private static final Set<String> VALID_USER_TYPES = Set.of("STUDENT", "LECTURER", "RESEARCHER", "STAFF");

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

        String resolvedChannel = normalizeOrDefault(channel, DEFAULT_CHANNEL).toUpperCase(Locale.ROOT);
        boolean isInstantInApp = DEFAULT_CHANNEL.equals(resolvedChannel);
        OffsetDateTime now = OffsetDateTime.now();

        MessageQueue message = MessageQueue.builder()
                .user(user)
                .circulation(circ)
                .hold(hold)
                .channel(resolvedChannel)
                .messageType(messageType)
                .content(content)
                .scheduledAt(now)
                .sentAt(isInstantInApp ? now : null)
                .status(isInstantInApp ? MessageStatus.SENT.name() : MessageStatus.PENDING.name())
                .retryCount(0)
                .build();

        return messageQueueRepository.save(message);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationItemDto> getMyNotifications(PaginationRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserIdOrThrow();
        Page<MessageQueue> page = messageQueueRepository.findByUserIdOrderByCreatedAtDesc(
                currentUserId,
                PageRequest.of(request.getPage(), request.getSize())
        );
        return PageResponse.from(page.map(this::toNotificationItemDto));
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationItemDto getMyNotificationById(Long notificationId) {
        UUID currentUserId = SecurityUtils.getCurrentUserIdOrThrow();
        MessageQueue message = messageQueueRepository.findByIdAndUserId(notificationId, currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông báo yêu cầu"));
        return toNotificationItemDto(message);
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadNotificationCountDto getMyUnreadCount() {
        UUID currentUserId = SecurityUtils.getCurrentUserIdOrThrow();
        long count = messageQueueRepository.countUnreadByUserId(currentUserId, MessageStatus.READ.name());
        return UnreadNotificationCountDto.builder().unreadCount(count).build();
    }

    @Override
    @Transactional
    public NotificationItemDto markAsRead(Long notificationId) {
        UUID currentUserId = SecurityUtils.getCurrentUserIdOrThrow();
        MessageQueue message = messageQueueRepository.findByIdAndUserId(notificationId, currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông báo yêu cầu"));

        if (!MessageStatus.READ.name().equalsIgnoreCase(message.getStatus())) {
            message.setStatus(MessageStatus.READ.name());
            if (message.getSentAt() == null) {
                message.setSentAt(OffsetDateTime.now());
            }
            message = messageQueueRepository.save(message);
        }
        return toNotificationItemDto(message);
    }

    @Override
    @Transactional
    public MarkNotificationsReadResponse markAllAsRead() {
        UUID currentUserId = SecurityUtils.getCurrentUserIdOrThrow();
        int updated = messageQueueRepository.markAllAsReadByUserId(currentUserId, MessageStatus.READ.name());
        return MarkNotificationsReadResponse.builder().updatedCount(updated).build();
    }

    @Override
    @Transactional
    public SendNotificationResponse sendNotification(SendNotificationRequest request) {
        String scope = request.getTargetScope() == null
                ? ""
                : request.getTargetScope().trim().toUpperCase(Locale.ROOT);

        List<UUID> recipientIds = resolveRecipientIds(scope, request);
        if (recipientIds.isEmpty()) {
            throw new AppException(
                    ErrorCode.INVALID_REQUEST,
                    "Không tìm thấy bạn đọc đang hoạt động phù hợp để gửi thông báo"
            );
        }

        String channel = normalizeOrDefault(request.getChannel(), DEFAULT_CHANNEL).toUpperCase(Locale.ROOT);
        String messageType = normalizeOrDefault(request.getMessageType(), DEFAULT_MESSAGE_TYPE).toUpperCase(Locale.ROOT);
        String cleanTitle = request.getTitle() != null ? request.getTitle().trim() : "";
        String cleanBody = request.getContent().trim();
        String storedContent = cleanTitle.isEmpty() ? cleanBody : cleanTitle + "\n" + cleanBody;
        OffsetDateTime now = OffsetDateTime.now();

        List<MessageQueue> messages = new ArrayList<>(recipientIds.size());
        for (UUID recipientId : recipientIds) {
            messages.add(MessageQueue.builder()
                    .user(userRepository.getReferenceById(recipientId))
                    .channel(channel)
                    .messageType(messageType)
                    .content(storedContent)
                    .scheduledAt(now)
                    .sentAt(now)
                    .status(MessageStatus.SENT.name())
                    .retryCount(0)
                    .build());
        }

        messageQueueRepository.saveAll(messages);
        log.info("Da gui {} thong bao [type={}, scope={}] thanh cong.", messages.size(), messageType, scope);

        return SendNotificationResponse.builder()
                .recipientCount(messages.size())
                .targetScope(scope)
                .messageType(messageType)
                .channel(channel)
                .title(cleanTitle.isEmpty() ? null : cleanTitle)
                .content(storedContent)
                .sentAt(now)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminNotificationItemDto> getAdminNotifications(
            String keyword,
            String messageType,
            String status,
            PaginationRequest request) {
        String keywordPattern = (keyword == null || keyword.trim().isEmpty())
                ? ""
                : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        String normalizedType = (messageType == null || messageType.trim().isEmpty())
                ? ""
                : messageType.trim().toUpperCase(Locale.ROOT);
        String normalizedStatus = (status == null || status.trim().isEmpty())
                ? ""
                : status.trim().toUpperCase(Locale.ROOT);

        Page<MessageQueue> page = messageQueueRepository.searchAdminMessages(
                keywordPattern,
                normalizedType,
                normalizedStatus,
                PageRequest.of(request.getPage(), request.getSize())
        );

        return PageResponse.from(page.map(this::toAdminNotificationItemDto));
    }

    @Override
    @Scheduled(cron = "0 0 8 * * *") // 8:00 AM hang ngay
    @Transactional
    public void scanOverdueLoansAndNotify() {
        log.info("Bat dau quét cac luot muon qua han de gui thong bao...");
        List<Circulation> overdueLoans = circulationRepository.findOverdueLoans(OffsetDateTime.now());

        for (Circulation loan : overdueLoans) {
            String content = String.format("Sách '%s' của bạn đã quá hạn trả vào ngày %s. Vui lòng trả sách hoặc liên hệ thư viện.",
                    loan.getPhysicalCopy().getDocument().getTitle(),
                    loan.getDueDate().toLocalDate());

            queueNotification(
                    loan.getUser().getId(),
                    loan.getId(),
                    null,
                    DEFAULT_CHANNEL,
                    "LOAN_OVERDUE",
                    content
            );
        }
        log.info("Da tao {} thong bao qua han cho ban doc.", overdueLoans.size());
    }

    private List<UUID> resolveRecipientIds(String scope, SendNotificationRequest request) {
        String readerRole = UserRole.READER.name();
        String activeStatus = UserStatus.ACTIVE.name();

        return switch (scope) {
            case "SINGLE_USER" -> {
                if (request.getUserId() == null) {
                    throw new AppException(ErrorCode.INVALID_REQUEST, "Vui lòng chọn bạn đọc nhận thông báo");
                }
                if (!userRepository.existsById(request.getUserId())) {
                    throw new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng nhận thông báo");
                }
                yield List.of(request.getUserId());
            }
            case "ALL_READERS" -> userRepository.findIdsByRoleAndStatus(readerRole, activeStatus);
            case "DEPARTMENT" -> {
                if (request.getDepartmentId() == null) {
                    throw new AppException(ErrorCode.INVALID_REQUEST, "Vui lòng chọn khoa/ngành nhận thông báo");
                }
                yield userRepository.findIdsByRoleAndStatusAndDepartmentId(
                        readerRole,
                        activeStatus,
                        request.getDepartmentId()
                );
            }
            case "USER_TYPE" -> {
                String normalizedUserType = request.getUserType() == null
                        ? ""
                        : request.getUserType().trim().toUpperCase(Locale.ROOT);
                if (normalizedUserType.isEmpty() || !VALID_USER_TYPES.contains(normalizedUserType)) {
                    throw new AppException(
                            ErrorCode.INVALID_REQUEST,
                            "Loại bạn đọc không hợp lệ (hỗ trợ STUDENT, LECTURER, RESEARCHER, STAFF)"
                    );
                }
                yield userRepository.findIdsByRoleAndStatusAndUserType(readerRole, activeStatus, normalizedUserType);
            }
            default -> throw new AppException(
                    ErrorCode.INVALID_REQUEST,
                    "Phạm vi người nhận không hợp lệ (hỗ trợ SINGLE_USER, ALL_READERS, DEPARTMENT, USER_TYPE)"
            );
        };
    }

    private String normalizeOrDefault(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private NotificationItemDto toNotificationItemDto(MessageQueue m) {
        return NotificationItemDto.builder()
                .id(m.getId())
                .channel(m.getChannel())
                .messageType(m.getMessageType())
                .content(m.getContent())
                .scheduledAt(m.getScheduledAt())
                .sentAt(m.getSentAt())
                .retryCount(m.getRetryCount())
                .errorMessage(m.getErrorMessage())
                .status(m.getStatus())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }

    private AdminNotificationItemDto toAdminNotificationItemDto(MessageQueue m) {
        User recipient = m.getUser();
        return AdminNotificationItemDto.builder()
                .id(m.getId())
                .userId(recipient != null ? recipient.getId() : null)
                .userCode(recipient != null ? recipient.getUserCode() : null)
                .fullName(recipient != null ? recipient.getFullName() : null)
                .email(recipient != null ? recipient.getEmail() : null)
                .channel(m.getChannel())
                .messageType(m.getMessageType())
                .content(m.getContent())
                .status(m.getStatus())
                .scheduledAt(m.getScheduledAt())
                .sentAt(m.getSentAt())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
