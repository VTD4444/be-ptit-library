package vn.edu.ptit.library.catalog.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.ptit.library.catalog.dto.DigitalCopyUploadResponse;
import vn.edu.ptit.library.catalog.dto.ProcessingStatusResponse;
import vn.edu.ptit.library.catalog.entity.DigitalCopy;

import java.io.IOException;
import java.util.UUID;

public interface DigitalCopyService {

    DigitalCopyUploadResponse uploadPdf(UUID documentId, MultipartFile file, String version) throws IOException;

    Resource downloadPdf(UUID digitalCopyId);

    Resource readCurrentPdf(UUID documentId);

    ProcessingStatusResponse getProcessingStatus(Long jobId);

    DigitalCopy getById(UUID digitalCopyId);
}
