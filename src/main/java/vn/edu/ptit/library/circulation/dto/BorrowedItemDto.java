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
public class BorrowedItemDto {

    private Long circulationId;
    private String barcode;
    private UUID documentId;
    private String documentTitle;
    private OffsetDateTime borrowDate;
    private OffsetDateTime dueDate;
    private String status;
}
