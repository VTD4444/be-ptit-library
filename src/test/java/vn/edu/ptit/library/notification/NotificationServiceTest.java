package vn.edu.ptit.library.notification;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.ptit.library.circulation.repository.CirculationRepository;
import vn.edu.ptit.library.circulation.repository.HoldQueueRepository;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;
import vn.edu.ptit.library.common.enums.MessageStatus;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;
import vn.edu.ptit.library.notification.dto.AdminNotificationItemDto;
import vn.edu.ptit.library.notification.dto.MarkNotificationsReadResponse;
import vn.edu.ptit.library.notification.dto.NotificationItemDto;
import vn.edu.ptit.library.notification.dto.SendNotificationRequest;
import vn.edu.ptit.library.notification.dto.SendNotificationResponse;
import vn.edu.ptit.library.notification.dto.UnreadNotificationCountDto;
import vn.edu.ptit.library.notification.entity.MessageQueue;
import vn.edu.ptit.library.notification.repository.MessageQueueRepository;
import vn.edu.ptit.library.notification.service.impl.NotificationServiceImpl;
import vn.edu.ptit.library.security.CustomUserDetails;
import vn.edu.ptit.library.user.entity.User;
import vn.edu.ptit.library.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private MessageQueueRepository messageQueueRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CirculationRepository circulationRepository;

    @Mock
    private HoldQueueRepository holdQueueRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UUID userId, String role) {
        CustomUserDetails principal = CustomUserDetails.builder()
                .id(userId)
                .email("reader@stu.ptit.edu.vn")
                .role(role)
                .status("ACTIVE")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @Test
    void queueNotification_InAppChannel_SavesImmediatelyAsSent() {
        UUID userId = UUID.randomUUID();
        when(userRepository.getReferenceById(userId)).thenReturn(User.builder().id(userId).build());
        when(messageQueueRepository.save(any(MessageQueue.class))).thenAnswer(inv -> inv.getArgument(0));

        MessageQueue saved = notificationService.queueNotification(userId, null, null, "IN_APP", "HOLD_PLACED", "Đặt trước thành công");

        assertEquals("IN_APP", saved.getChannel());
        assertEquals(MessageStatus.SENT.name(), saved.getStatus());
        assertNotNull(saved.getSentAt());
    }

    @Test
    void queueNotification_EmailChannel_RemainsPendingUntilDelivered() {
        UUID userId = UUID.randomUUID();
        when(userRepository.getReferenceById(userId)).thenReturn(User.builder().id(userId).build());
        when(messageQueueRepository.save(any(MessageQueue.class))).thenAnswer(inv -> inv.getArgument(0));

        MessageQueue saved = notificationService.queueNotification(userId, null, null, "EMAIL", "LOAN_OVERDUE", "Sách quá hạn");

        assertEquals("EMAIL", saved.getChannel());
        assertEquals(MessageStatus.PENDING.name(), saved.getStatus());
        assertNull(saved.getSentAt());
    }

    @Test
    void markAsRead_OwnerNotification_UpdatesStatusToRead() {
        UUID userId = UUID.randomUUID();
        authenticateAs(userId, "READER");
        MessageQueue msg = MessageQueue.builder()
                .id(42L)
                .channel("IN_APP")
                .messageType("SYSTEM_ANNOUNCEMENT")
                .content("Thông báo lịch nghỉ")
                .status(MessageStatus.SENT.name())
                .sentAt(OffsetDateTime.now())
                .build();
        when(messageQueueRepository.findByIdAndUserId(42L, userId)).thenReturn(Optional.of(msg));
        when(messageQueueRepository.save(any(MessageQueue.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationItemDto dto = notificationService.markAsRead(42L);

        assertEquals(42L, dto.getId());
        assertEquals(MessageStatus.READ.name(), dto.getStatus());
        verify(messageQueueRepository).save(msg);
    }

    @Test
    void markAsRead_MissingOrOtherUserNotification_ThrowsNotFound() {
        UUID userId = UUID.randomUUID();
        authenticateAs(userId, "READER");
        when(messageQueueRepository.findByIdAndUserId(99L, userId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> notificationService.markAsRead(99L));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void markAllAsRead_AndUnreadCount_UsesCurrentUser() {
        UUID userId = UUID.randomUUID();
        authenticateAs(userId, "READER");
        when(messageQueueRepository.countUnreadByUserId(userId, MessageStatus.READ.name())).thenReturn(3L);
        when(messageQueueRepository.markAllAsReadByUserId(userId, MessageStatus.READ.name())).thenReturn(3);

        UnreadNotificationCountDto unread = notificationService.getMyUnreadCount();
        assertEquals(3L, unread.getUnreadCount());

        MarkNotificationsReadResponse response = notificationService.markAllAsRead();
        assertEquals(3, response.getUpdatedCount());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendNotification_SingleUser_SavesFormattedMessage() {
        UUID userId = UUID.randomUUID();
        User userRef = User.builder().id(userId).userCode("B22DCPT058").fullName("Dinh Vu Trung Duc").build();
        when(userRepository.existsById(userId)).thenReturn(true);
        when(userRepository.getReferenceById(userId)).thenReturn(userRef);
        when(messageQueueRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        SendNotificationRequest request = SendNotificationRequest.builder()
                .targetScope("SINGLE_USER")
                .userId(userId)
                .messageType("LIBRARY_NOTICE")
                .title("Lịch nghỉ lễ 30/4")
                .content("Thư viện nghỉ từ ngày 30/04 đến hết 01/05.")
                .build();

        SendNotificationResponse response = notificationService.sendNotification(request);

        assertEquals(1, response.getRecipientCount());
        assertEquals("SINGLE_USER", response.getTargetScope());
        assertEquals("LIBRARY_NOTICE", response.getMessageType());
        assertEquals("IN_APP", response.getChannel());
        assertEquals("Lịch nghỉ lễ 30/4", response.getTitle());
        assertEquals("Lịch nghỉ lễ 30/4\nThư viện nghỉ từ ngày 30/04 đến hết 01/05.", response.getContent());
        assertNotNull(response.getSentAt());

        ArgumentCaptor<List<MessageQueue>> captor = ArgumentCaptor.forClass(List.class);
        verify(messageQueueRepository).saveAll(captor.capture());
        List<MessageQueue> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals(MessageStatus.SENT.name(), saved.get(0).getStatus());
        assertNotNull(saved.get(0).getSentAt());
        assertEquals(userRef, saved.get(0).getUser());
    }

    @Test
    void sendNotification_SingleUserNotFound_ThrowsUserNotFound() {
        UUID missingId = UUID.randomUUID();
        when(userRepository.existsById(missingId)).thenReturn(false);

        SendNotificationRequest request = SendNotificationRequest.builder()
                .targetScope("SINGLE_USER")
                .userId(missingId)
                .content("Xin chào")
                .build();

        AppException ex = assertThrows(AppException.class, () -> notificationService.sendNotification(request));
        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendNotification_AllReaders_BatchSavesAllActiveReaders() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        when(userRepository.findIdsByRoleAndStatus("READER", "ACTIVE")).thenReturn(List.of(u1, u2));
        when(userRepository.getReferenceById(u1)).thenReturn(User.builder().id(u1).build());
        when(userRepository.getReferenceById(u2)).thenReturn(User.builder().id(u2).build());
        when(messageQueueRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        SendNotificationRequest request = SendNotificationRequest.builder()
                .targetScope("ALL_READERS")
                .content("Cập nhật giờ mở cửa phòng đọc tầng 2.")
                .build();

        SendNotificationResponse response = notificationService.sendNotification(request);

        assertEquals(2, response.getRecipientCount());
        assertEquals("SYSTEM_ANNOUNCEMENT", response.getMessageType());
        assertEquals("Cập nhật giờ mở cửa phòng đọc tầng 2.", response.getContent());

        ArgumentCaptor<List<MessageQueue>> captor = ArgumentCaptor.forClass(List.class);
        verify(messageQueueRepository).saveAll(captor.capture());
        assertEquals(2, captor.getValue().size());
    }

    @Test
    void sendNotification_DepartmentWithNoReaders_ThrowsInvalidRequest() {
        when(userRepository.findIdsByRoleAndStatusAndDepartmentId("READER", "ACTIVE", 99L)).thenReturn(List.of());

        SendNotificationRequest request = SendNotificationRequest.builder()
                .targetScope("DEPARTMENT")
                .departmentId(99L)
                .content("Thông báo riêng khoa")
                .build();

        AppException ex = assertThrows(AppException.class, () -> notificationService.sendNotification(request));
        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    void sendNotification_InvalidUserType_ThrowsInvalidRequest() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .targetScope("USER_TYPE")
                .userType("ALIEN")
                .content("Nội dung")
                .build();

        AppException ex = assertThrows(AppException.class, () -> notificationService.sendNotification(request));
        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    void getAdminNotifications_MapsRecipientAndNormalizesFilters() {
        UUID userId = UUID.randomUUID();
        User recipient = User.builder()
                .id(userId)
                .userCode("B22DCPT058")
                .fullName("Dinh Vu Trung Duc")
                .email("ducdvt.b22pt058@stu.ptit.edu.vn")
                .build();
        MessageQueue msg = MessageQueue.builder()
                .id(15L)
                .user(recipient)
                .channel("IN_APP")
                .messageType("SYSTEM_ANNOUNCEMENT")
                .content("Thông báo bảo trì\nHệ thống bảo trì lúc 23h.")
                .status("READ")
                .sentAt(OffsetDateTime.now())
                .build();

        when(messageQueueRepository.searchAdminMessages(
                eq("%bảo trì%"),
                eq("SYSTEM_ANNOUNCEMENT"),
                eq("READ"),
                eq(PageRequest.of(0, 10))
        )).thenReturn(new PageImpl<>(List.of(msg), PageRequest.of(0, 10), 1));

        PageResponse<AdminNotificationItemDto> page = notificationService.getAdminNotifications(
                "  Bảo Trì ",
                "system_announcement",
                " read ",
                PaginationRequest.builder().page(0).size(10).build()
        );

        assertEquals(1, page.getItems().size());
        AdminNotificationItemDto dto = page.getItems().get(0);
        assertEquals(15L, dto.getId());
        assertEquals(userId, dto.getUserId());
        assertEquals("B22DCPT058", dto.getUserCode());
        assertEquals("Dinh Vu Trung Duc", dto.getFullName());
        assertEquals("SYSTEM_ANNOUNCEMENT", dto.getMessageType());
        assertEquals("READ", dto.getStatus());
    }
}
