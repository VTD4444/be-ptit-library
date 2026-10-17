package vn.edu.ptit.library.integration.python.dto;

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
public class PdfIndexRequest {

    private UUID digitalCopyId;
    private UUID documentId;
    private String filePath;
    private Long searchConfigId;
    private Long backgroundJobId;
}
