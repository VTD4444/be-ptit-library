package vn.edu.ptit.library.notification.service;

import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;
import vn.edu.ptit.library.notification.entity.MessageQueue;

import java.util.UUID;

public interface NotificationService {

    MessageQueue queueNotification(UUID userId, Long circulationId, Long holdId, String channel, String messageType, String content);

    PageResponse<MessageQueue> getMyNotifications(PaginationRequest request);

    void scanOverdueLoansAndNotify();
}
