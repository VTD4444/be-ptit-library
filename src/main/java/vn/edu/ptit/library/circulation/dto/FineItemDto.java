package vn.edu.ptit.library.circulation.dto;

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
public class FineItemDto {

    private Long fineId;
    private Long circulationId;
    private String barcode;
    private String documentTitle;
    private String penaltyCode;
    private String reason;
    private int quantity;
    private long unitPrice;
    private long fineAmount;
    private String status;
    private String note;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
