package vn.edu.ptit.library.analytics.dto;

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
public class LowCirculationDocumentDto {

    private UUID documentId;
    private String title;
    private String docType;
    private long borrowCount;
    private OffsetDateTime lastBorrowedAt;
}
