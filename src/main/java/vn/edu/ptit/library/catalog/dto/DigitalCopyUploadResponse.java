package vn.edu.ptit.library.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DigitalCopyUploadResponse {

    private UUID digitalCopyId;
    private UUID documentId;
    private String version;
    private String fileName;
    private Long fileSize;

    // Trang thai luu tep: STORED khi tep da ghi xuong storage thanh cong
    private String fileStatus;
    private String processingStatus;

    // ID tien trinh boc tach ngam, dung cho GET /api/admin/digital-copies/processing-status
    private Long jobId;
    private String jobStatus;
}
