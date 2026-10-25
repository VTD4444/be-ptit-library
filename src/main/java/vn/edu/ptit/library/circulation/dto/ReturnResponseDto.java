package vn.edu.ptit.library.circulation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnResponseDto {

    private Long circulationId;
    private String barcode;
    private UUID documentId;
    private String documentTitle;
    private String userCode;
    private OffsetDateTime borrowDate;
    private OffsetDateTime dueDate;
    private OffsetDateTime returnDate;
    private long overdueDays;
    private long totalFineAmount;
    private String updatedCopyStatus;
    private String assignedHoldUserCode;
    private List<FineItemDto> generatedFines;
}
