package vn.edu.ptit.library.catalog.dto;

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
public class ProcessingStatusResponse {

    private Long jobId;
    private UUID digitalCopyId;

    // QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED
    private String status;
    // Cho, Dang chay, Xong, Loi, Da huy
    private String statusLabel;

    // PENDING, PROCESSING, INDEXED, LOW_QUALITY, FAILED
    private String digitalCopyStatus;
    private Integer pageCount;
    private Double extractionQuality;

    private String errorMessage;
    private Integer attempts;
    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
}
