package vn.edu.ptit.library.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.library.common.dto.ApiResponse;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;
import vn.edu.ptit.library.notification.dto.AdminNotificationItemDto;
import vn.edu.ptit.library.notification.dto.MarkNotificationsReadResponse;
import vn.edu.ptit.library.notification.dto.NotificationItemDto;
import vn.edu.ptit.library.notification.dto.SendNotificationRequest;
import vn.edu.ptit.library.notification.dto.SendNotificationResponse;
import vn.edu.ptit.library.notification.dto.UnreadNotificationCountDto;
import vn.edu.ptit.library.notification.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Quan ly thong bao, nhac han tra sach va tin nhan den ban doc")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/my-messages")
    @Operation(summary = "Lay danh sach thong bao cua ban doc hien tai")
    public ApiResponse<PageResponse<NotificationItemDto>> getMyNotifications(@Valid @ModelAttribute PaginationRequest request) {
        return ApiResponse.success(notificationService.getMyNotifications(request));
    }

    @GetMapping("/my-messages/unread-count")
    @Operation(summary = "Dem so thong bao chua doc cua nguoi dung hien tai")
    public ApiResponse<UnreadNotificationCountDto> getMyUnreadCount() {
        return ApiResponse.success(notificationService.getMyUnreadCount());
    }

    @GetMapping("/my-messages/{id}")
    @Operation(summary = "Xem chi tiet mot thong bao cua nguoi dung hien tai")
    public ApiResponse<NotificationItemDto> getMyNotificationById(@PathVariable Long id) {
        return ApiResponse.success(notificationService.getMyNotificationById(id));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Danh dau mot thong bao la da doc")
    public ApiResponse<NotificationItemDto> markAsRead(@PathVariable Long id) {
        return ApiResponse.success("Đã đánh dấu thông báo là đã đọc", notificationService.markAsRead(id));
    }

    @PatchMapping("/my-messages/read-all")
    @Operation(summary = "Danh dau tat ca thong bao cua nguoi dung hien tai la da doc")
    public ApiResponse<MarkNotificationsReadResponse> markAllAsRead() {
        return ApiResponse.success("Đã đánh dấu tất cả thông báo là đã đọc", notificationService.markAllAsRead());
    }

    @PostMapping("/admin/send")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    @Operation(summary = "Admin/Thu thu gui thong bao toi 1 ban doc, theo khoa, nhom ban doc hoac toan he thong")
    public ApiResponse<SendNotificationResponse> sendNotification(@Valid @RequestBody SendNotificationRequest request) {
        return ApiResponse.success("Đã gửi thông báo thành công", notificationService.sendNotification(request));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    @Operation(summary = "Admin/Thu thu xem lich su thong bao da gui")
    public ApiResponse<PageResponse<AdminNotificationItemDto>> getAdminNotifications(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String messageType,
            @RequestParam(required = false) String status,
            @Valid @ModelAttribute PaginationRequest request) {
        return ApiResponse.success(notificationService.getAdminNotifications(keyword, messageType, status, request));
    }
}
