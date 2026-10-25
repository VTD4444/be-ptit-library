package vn.edu.ptit.library.circulation.dto;

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
public class CirculationHistoryItemDto {

    private Long circulationId;
    private UUID documentId;
    private String documentTitle;
    private String barcode;
    private String callNumber;
    private String shelfLocation;
    private OffsetDateTime borrowDate;
    private OffsetDateTime dueDate;
    private OffsetDateTime returnDate;
    private int renewalsCount;
    private String status;
    private long fineAmount;
}
