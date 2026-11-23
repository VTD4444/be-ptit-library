package vn.edu.ptit.library.circulation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Mot luot dat truoc trong danh sach quan ly cua thu thu (GET /api/holds).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffHoldItemDto {

    private Long holdId;
    private String status;
    private Integer queuePosition;
    private OffsetDateTime requestDate;
    private OffsetDateTime readyDate;
    private OffsetDateTime pickupDeadline;
    private String cancelReason;

    private UUID documentId;
    private String documentTitle;
    private String docType;
    private Integer publicationYear;

    private UUID userId;
    private String userCode;
    private String fullName;
    private String email;
    private String phone;

    /** Ban sach dang giu (READY_FOR_PICKUP). */
    private String barcode;
    private String shelfLocation;
}
