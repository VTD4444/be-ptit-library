package vn.edu.ptit.library.catalog.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.ptit.library.catalog.dto.DigitalCopyUploadResponse;
import vn.edu.ptit.library.catalog.dto.ProcessingStatusResponse;
import vn.edu.ptit.library.catalog.entity.DigitalCopy;
import vn.edu.ptit.library.catalog.entity.Document;
import vn.edu.ptit.library.catalog.repository.DigitalCopyRepository;
import vn.edu.ptit.library.catalog.repository.DocumentRepository;
import vn.edu.ptit.library.catalog.service.DigitalCopyService;
import vn.edu.ptit.library.common.enums.DigitalProcessingStatus;
import vn.edu.ptit.library.common.enums.JobStatus;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;
import vn.edu.ptit.library.common.util.SecurityUtils;
import vn.edu.ptit.library.indexing.entity.BackgroundJob;
import vn.edu.ptit.library.indexing.service.IndexingJobService;
import vn.edu.ptit.library.integration.storage.StorageService;
import vn.edu.ptit.library.search.entity.SearchVectorConfig;
import vn.edu.ptit.library.search.repository.SearchVectorConfigRepository;
import vn.edu.ptit.library.user.entity.User;
import vn.edu.ptit.library.user.repository.UserRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DigitalCopyServiceImpl implements DigitalCopyService {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final DigitalCopyRepository digitalCopyRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final SearchVectorConfigRepository searchVectorConfigRepository;
    private final IndexingJobService indexingJobService;

    @Override
    @Transactional
    public DigitalCopyUploadResponse uploadPdf(UUID documentId, MultipartFile file, String version) throws IOException {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));

        validatePdf(file);

        String hash = storageService.calculateSha256(file);
        // Ban bi boc tach loi (FAILED) khong tinh la trung: cho phep tai lai cung tep de chay lai
        if (digitalCopyRepository.existsByDocumentIdAndFileHashAndProcessingStatusNot(
                documentId, hash, DigitalProcessingStatus.FAILED.name())) {
            throw new AppException(ErrorCode.DUPLICATE_RESOURCE, "Tệp PDF này đã được tải lên cho tài liệu");
        }

        String relativePath = storageService.storePdf(file, "docs/" + documentId);
        deleteFileOnRollback(relativePath);

        // Dat cac ban cu ve isCurrent = false
        digitalCopyRepository.findByDocumentIdAndIsCurrentTrue(documentId).ifPresent(oldCopy -> {
            oldCopy.setIsCurrent(false);
            digitalCopyRepository.save(oldCopy);
        });

        User currentUser = SecurityUtils.getCurrentUserDetails()
                .flatMap(u -> userRepository.findById(u.getId()))
                .orElse(null);

        DigitalCopy digitalCopy = digitalCopyRepository.save(DigitalCopy.builder()
                .document(document)
                .version(version != null && !version.isBlank() ? version : "1.0")
                .isCurrent(true)
                .processingStatus(DigitalProcessingStatus.PENDING.name())
                .filePath(relativePath)
                .fileHash(hash)
                .fileSize(file.getSize())
                .uploadedBy(currentUser)
                .build());

        Long searchConfigId = searchVectorConfigRepository.findByIsActiveTrue()
                .map(SearchVectorConfig::getId)
                .orElse(null);

        // Python Worker chay cung may chu nen nhan duong dan tuyet doi toi tep PDF
        BackgroundJob job = indexingJobService.enqueuePdfIndexingJob(
                digitalCopy.getId(),
                documentId,
                storageService.resolveAbsolutePath(relativePath).toString(),
                searchConfigId);

        return DigitalCopyUploadResponse.builder()
                .digitalCopyId(digitalCopy.getId())
                .documentId(documentId)
                .version(digitalCopy.getVersion())
                .fileSize(digitalCopy.getFileSize())
                .fileStatus("STORED")
                .processingStatus(digitalCopy.getProcessingStatus())
                .jobId(job.getId())
                .jobStatus(job.getStatus())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadPdf(UUID digitalCopyId) {
        DigitalCopy digitalCopy = getById(digitalCopyId);
        return storageService.loadAsResource(digitalCopy.getFilePath());
    }

    @Override
    @Transactional(readOnly = true)
    public Resource readCurrentPdf(UUID documentId) {
        if (!documentRepository.existsById(documentId)) {
            throw new AppException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        DigitalCopy digitalCopy = digitalCopyRepository.findByDocumentIdAndIsCurrentTrue(documentId)
                .orElseThrow(() -> new AppException(ErrorCode.DIGITAL_COPY_NOT_FOUND, "Tài liệu này chưa có bản số để đọc trực tuyến"));
        return storageService.loadAsResource(digitalCopy.getFilePath());
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessingStatusResponse getProcessingStatus(Long jobId) {
        BackgroundJob job = indexingJobService.getJobStatus(jobId);
        if (!IndexingJobService.PDF_INDEXING_JOB_TYPE.equals(job.getJobType())) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tiến trình bóc tách PDF");
        }

        DigitalCopy digitalCopy = job.getTargetId() != null
                ? digitalCopyRepository.findById(job.getTargetId()).orElse(null)
                : null;

        return ProcessingStatusResponse.builder()
                .jobId(job.getId())
                .digitalCopyId(job.getTargetId())
                .status(job.getStatus())
                .statusLabel(toStatusLabel(job.getStatus()))
                .digitalCopyStatus(digitalCopy != null ? digitalCopy.getProcessingStatus() : null)
                .pageCount(digitalCopy != null ? digitalCopy.getPageCount() : null)
                .extractionQuality(digitalCopy != null ? digitalCopy.getExtractionQuality() : null)
                .errorMessage(job.getErrorMessage())
                .attempts(job.getAttempts())
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .finishedAt(job.getFinishedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DigitalCopy getById(UUID digitalCopyId) {
        return digitalCopyRepository.findById(digitalCopyId)
                .orElseThrow(() -> new AppException(ErrorCode.DIGITAL_COPY_NOT_FOUND));
    }

    private void validatePdf(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (file.isEmpty() || filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Tệp tải lên phải là định dạng PDF không rỗng");
        }
        try (InputStream is = file.getInputStream()) {
            if (!Arrays.equals(is.readNBytes(PDF_MAGIC.length), PDF_MAGIC)) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "Nội dung tệp không phải PDF hợp lệ");
            }
        }
    }

    private void deleteFileOnRollback(String relativePath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    storageService.deleteFile(relativePath);
                }
            }
        });
    }

    private static String toStatusLabel(String status) {
        if (status == null) {
            return null;
        }
        // Python Worker cung ghi cot status: gia tri la khong lam hong API tra cuu
        JobStatus jobStatus;
        try {
            jobStatus = JobStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            return status;
        }
        return switch (jobStatus) {
            case QUEUED -> "Chờ";
            case RUNNING -> "Đang chạy";
            case COMPLETED -> "Xong";
            case FAILED -> "Lỗi";
            case CANCELLED -> "Đã hủy";
        };
    }
}
