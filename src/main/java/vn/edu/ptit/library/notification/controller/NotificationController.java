package vn.edu.ptit.library.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.library.common.dto.ApiResponse;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;
import vn.edu.ptit.library.notification.entity.MessageQueue;
import vn.edu.ptit.library.notification.service.NotificationService;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Quan ly thong bao, nhac han tra sach va tin nhan den ban doc")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/my-messages")
    @Operation(summary = "Lay danh sach thong bao cua ban doc hien tai")
    public ApiResponse<PageResponse<MessageQueue>> getMyNotifications(@Valid @ModelAttribute PaginationRequest request) {
        return ApiResponse.success(notificationService.getMyNotifications(request));
    }
}
