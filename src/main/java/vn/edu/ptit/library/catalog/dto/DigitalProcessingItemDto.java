package vn.edu.ptit.library.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Mot ban so PDF kem tien trinh boc tach gan nhat (GET /api/admin/digital-copies).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DigitalProcessingItemDto {

    private UUID digitalCopyId;
    private UUID documentId;
    private String documentTitle;
    private String fileName;
    private Long fileSize;
    private String version;
    private Boolean isCurrent;
    private Integer pageCount;
    private Double extractionQuality;
    /** PENDING, PROCESSING, INDEXED, LOW_QUALITY, FAILED */
    private String processingStatus;
    private OffsetDateTime uploadedAt;
    private String uploadedBy;

    /** Tien trinh boc tach gan nhat (null neu ban so duoc nap ngoai he thong hang doi). */
    private Long jobId;
    private String jobStatus;
    private Integer attempts;
    private String errorMessage;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
}
