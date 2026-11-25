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
public class SendNotificationResponse {

    private int recipientCount;
    private String targetScope;
    private String messageType;
    private String channel;
    private String title;
    private String content;
    private OffsetDateTime sentAt;
}
