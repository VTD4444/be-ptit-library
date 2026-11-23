package vn.edu.ptit.library.circulation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Tra cuu nhanh mot ban sach theo ma vach tai quay luu thong (GET /api/circulation/copies/{barcode}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopyLookupDto {

    private UUID copyId;
    private String barcode;
    private String callNumber;
    private String shelfLocation;
    /** AVAILABLE, BORROWED, RESERVED, DAMAGED, LOST, ... */
    private String status;

    private UUID documentId;
    private String documentTitle;
    private List<String> authors;
    private String docType;
    private Integer publicationYear;

    /** Luot muon dang mo cua ban sach (neu co). */
    private ActiveLoan activeLoan;
    /** Luot dat truoc dang giu ban sach nay (status = RESERVED). */
    private ReservedHold reservedFor;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveLoan {
        private Long circulationId;
        private String userCode;
        private String fullName;
        private OffsetDateTime borrowDate;
        private OffsetDateTime dueDate;
        private int renewalsCount;
        private long overdueDays;
        /** Phi qua han du kien neu tra ngay bay gio. */
        private long estimatedOverdueFine;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReservedHold {
        private Long holdId;
        private String userCode;
        private String fullName;
        private OffsetDateTime pickupDeadline;
    }
}
