package vn.edu.ptit.library.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.notification.controller.NotificationController;
import vn.edu.ptit.library.notification.dto.AdminNotificationItemDto;
import vn.edu.ptit.library.notification.dto.MarkNotificationsReadResponse;
import vn.edu.ptit.library.notification.dto.NotificationItemDto;
import vn.edu.ptit.library.notification.dto.SendNotificationResponse;
import vn.edu.ptit.library.notification.dto.UnreadNotificationCountDto;
import vn.edu.ptit.library.notification.service.NotificationService;
import vn.edu.ptit.library.security.JwtAuthenticationEntryPoint;
import vn.edu.ptit.library.security.JwtAuthenticationFilter;
import vn.edu.ptit.library.security.JwtTokenProvider;
import vn.edu.ptit.library.security.SecurityConfig;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class NotificationApiTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @ParameterizedTest
    @CsvSource({
            "ADMIN,200",
            "LIBRARIAN,200",
            "READER,403"
    })
    void adminNotificationsList_RoleMatrix(String role, int expectedStatus) throws Exception {
        when(notificationService.getAdminNotifications(any(), any(), any(), any())).thenReturn(
                PageResponse.<AdminNotificationItemDto>builder()
                        .items(List.of(AdminNotificationItemDto.builder()
                                .id(1L)
                                .userId(UUID.randomUUID())
                                .userCode("B22DCPT058")
                                .fullName("Dinh Vu Trung Duc")
                                .messageType("SYSTEM_ANNOUNCEMENT")
                                .content("Thông báo chung")
                                .status("SENT")
                                .build()))
                        .totalElements(1)
                        .totalPages(1)
                        .page(0)
                        .size(20)
                        .build()
        );

        mvc.perform(get("/api/notifications/admin?status=SENT").with(user("tester").roles(role)))
                .andExpect(status().is(expectedStatus));

        if (expectedStatus == 403) {
            verifyNoInteractions(notificationService);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "ADMIN,200",
            "LIBRARIAN,200",
            "READER,403"
    })
    void adminSendNotification_RoleMatrix(String role, int expectedStatus) throws Exception {
        when(notificationService.sendNotification(any())).thenReturn(
                SendNotificationResponse.builder()
                        .recipientCount(5)
                        .targetScope("ALL_READERS")
                        .messageType("SYSTEM_ANNOUNCEMENT")
                        .channel("IN_APP")
                        .title("Thông báo mới")
                        .content("Thông báo mới\nNội dung chi tiết")
                        .sentAt(OffsetDateTime.now())
                        .build()
        );

        mvc.perform(post("/api/notifications/admin/send")
                        .with(user("tester").roles(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetScope\":\"ALL_READERS\",\"title\":\"Thông báo mới\",\"content\":\"Nội dung chi tiết\"}"))
                .andExpect(status().is(expectedStatus));

        if (expectedStatus == 403) {
            verifyNoInteractions(notificationService);
        }
    }

    @Test
    void adminSendNotification_BlankContent_ReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/notifications/admin/send")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetScope\":\"ALL_READERS\",\"content\":\"   \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(notificationService);
    }

    @Test
    void myNotifications_AndReadEndpoints_WorkForAuthenticatedReader() throws Exception {
        when(notificationService.getMyNotifications(any())).thenReturn(
                PageResponse.<NotificationItemDto>builder()
                        .items(List.of(NotificationItemDto.builder()
                                .id(10L)
                                .channel("IN_APP")
                                .messageType("SYSTEM_ANNOUNCEMENT")
                                .content("Tiêu đề\nNội dung")
                                .status("SENT")
                                .build()))
                        .totalElements(1)
                        .totalPages(1)
                        .page(0)
                        .size(20)
                        .build()
        );
        when(notificationService.getMyUnreadCount()).thenReturn(
                UnreadNotificationCountDto.builder().unreadCount(2).build()
        );
        when(notificationService.getMyNotificationById(10L)).thenReturn(
                NotificationItemDto.builder().id(10L).status("SENT").content("Tiêu đề\nNội dung").build()
        );
        when(notificationService.markAsRead(10L)).thenReturn(
                NotificationItemDto.builder().id(10L).status("READ").content("Tiêu đề\nNội dung").build()
        );
        when(notificationService.markAllAsRead()).thenReturn(
                MarkNotificationsReadResponse.builder().updatedCount(2).build()
        );

        mvc.perform(get("/api/notifications/my-messages").with(user("reader").roles("READER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(10))
                .andExpect(jsonPath("$.data.items[0].messageType").value("SYSTEM_ANNOUNCEMENT"))
                .andExpect(jsonPath("$.data.items[0].user").doesNotExist());

        mvc.perform(get("/api/notifications/my-messages/unread-count").with(user("reader").roles("READER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(2));

        mvc.perform(get("/api/notifications/my-messages/10").with(user("reader").roles("READER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10));

        mvc.perform(patch("/api/notifications/10/read").with(user("reader").roles("READER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READ"));

        mvc.perform(patch("/api/notifications/my-messages/read-all").with(user("reader").roles("READER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount").value(2));

        mvc.perform(get("/api/notifications/admin")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/notifications/10/read")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/notifications/my-messages/read-all")).andExpect(status().isUnauthorized());
    }
}
