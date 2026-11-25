package vn.edu.ptit.library.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendNotificationRequest {

    /**
     * Pham vi gui: SINGLE_USER, ALL_READERS, DEPARTMENT, USER_TYPE.
     */
    @NotBlank(message = "Phạm vi người nhận không được để trống")
    private String targetScope;

    /**
     * Bat buoc khi targetScope = SINGLE_USER.
     */
    private UUID userId;

    /**
     * Bat buoc khi targetScope = DEPARTMENT.
     */
    private Long departmentId;

    /**
     * Bat buoc khi targetScope = USER_TYPE (STUDENT, LECTURER, RESEARCHER, STAFF).
     */
    private String userType;

    /**
     * Loai thong bao: SYSTEM_ANNOUNCEMENT, LIBRARY_NOTICE, REMINDER, MAINTENANCE, GENERAL...
     */
    @Size(max = 50, message = "Loại thông báo không vượt quá 50 ký tự")
    private String messageType;

    /**
     * Tieu de ngan gon (tuy chon).
     */
    @Size(max = 160, message = "Tiêu đề thông báo không vượt quá 160 ký tự")
    private String title;

    /**
     * Noi dung chi tiet cua thong bao.
     */
    @NotBlank(message = "Nội dung thông báo không được để trống")
    @Size(max = 2000, message = "Nội dung thông báo không vượt quá 2000 ký tự")
    private String content;

    /**
     * Kenh gui (mac dinh IN_APP).
     */
    @Size(max = 30, message = "Kênh gửi không vượt quá 30 ký tự")
    private String channel;
}
