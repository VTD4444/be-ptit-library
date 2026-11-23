package vn.edu.ptit.library.circulation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Mot luot dat truoc cua ban doc dang dang nhap (GET /api/users/me/holds).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyHoldItemDto {

    private Long holdId;
    private UUID documentId;
    private String documentTitle;
    private String docType;
    private Integer publicationYear;
    private String coverUrl;

    /** WAITING, READY_FOR_PICKUP, FULFILLED, EXPIRED, CANCELLED. */
    private String status;
    /** Vi tri trong hang cho; chi co y nghia khi status = WAITING. */
    private Integer queuePosition;
    private OffsetDateTime requestDate;
    /** Ngay du kien co sach (chi tinh cho WAITING). */
    private OffsetDateTime estimatedAvailableDate;

    /** Thoi diem sach duoc giu cho ban doc va han cuoi den nhan (READY_FOR_PICKUP). */
    private OffsetDateTime readyDate;
    private OffsetDateTime pickupDeadline;
    /** Ban sach dang duoc giu (READY_FOR_PICKUP). */
    private String barcode;
    private String callNumber;
    private String shelfLocation;

    private String cancelReason;
}
