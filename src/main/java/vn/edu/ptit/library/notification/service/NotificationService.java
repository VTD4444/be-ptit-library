package vn.edu.ptit.library.notification.service;

import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;
import vn.edu.ptit.library.notification.dto.AdminNotificationItemDto;
import vn.edu.ptit.library.notification.dto.MarkNotificationsReadResponse;
import vn.edu.ptit.library.notification.dto.NotificationItemDto;
import vn.edu.ptit.library.notification.dto.SendNotificationRequest;
import vn.edu.ptit.library.notification.dto.SendNotificationResponse;
import vn.edu.ptit.library.notification.dto.UnreadNotificationCountDto;
import vn.edu.ptit.library.notification.entity.MessageQueue;

import java.util.UUID;

public interface NotificationService {

    MessageQueue queueNotification(UUID userId, Long circulationId, Long holdId, String channel, String messageType, String content);

    PageResponse<NotificationItemDto> getMyNotifications(PaginationRequest request);

    NotificationItemDto getMyNotificationById(Long notificationId);

    UnreadNotificationCountDto getMyUnreadCount();

    NotificationItemDto markAsRead(Long notificationId);

    MarkNotificationsReadResponse markAllAsRead();

    SendNotificationResponse sendNotification(SendNotificationRequest request);

    PageResponse<AdminNotificationItemDto> getAdminNotifications(
            String keyword,
            String messageType,
            String status,
            PaginationRequest request);

    void scanOverdueLoansAndNotify();
}
