package vn.edu.ptit.library.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationItemDto {

    private Long id;
    private String channel;
    private String messageType;
    private String content;
    private OffsetDateTime scheduledAt;
    private OffsetDateTime sentAt;
    private Integer retryCount;
    private String errorMessage;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
