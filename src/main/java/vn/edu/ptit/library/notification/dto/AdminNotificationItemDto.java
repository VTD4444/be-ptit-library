package vn.edu.ptit.library.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminNotificationItemDto {

    private Long id;
    private UUID userId;
    private String userCode;
    private String fullName;
    private String email;
    private String channel;
    private String messageType;
    private String content;
    private String status;
    private OffsetDateTime scheduledAt;
    private OffsetDateTime sentAt;
    private OffsetDateTime createdAt;
}
