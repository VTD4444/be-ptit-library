package vn.edu.ptit.library.catalog.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.ptit.library.catalog.dto.DigitalCopyUploadResponse;
import vn.edu.ptit.library.catalog.dto.DigitalProcessingItemDto;
import vn.edu.ptit.library.catalog.dto.DigitalProcessingListResponse;
import vn.edu.ptit.library.catalog.dto.ProcessingStatusResponse;
import vn.edu.ptit.library.catalog.entity.DigitalCopy;

import java.io.IOException;
import java.util.UUID;

public interface DigitalCopyService {

    DigitalCopyUploadResponse uploadPdf(UUID documentId, MultipartFile file, String version) throws IOException;

    Resource downloadPdf(UUID digitalCopyId);

    Resource readCurrentPdf(UUID documentId);

    ProcessingStatusResponse getProcessingStatus(Long jobId);

    /** Danh sach ban so kem tien trinh boc tach gan nhat; status = mot DigitalProcessingStatus hoac bo trong. */
    DigitalProcessingListResponse listForProcessing(String status, String keyword, int page, int size);

    /** Chay lai boc tach cho ban so FAILED / LOW_QUALITY. */
    DigitalProcessingItemDto reprocess(UUID digitalCopyId);

    DigitalCopy getById(UUID digitalCopyId);
}
