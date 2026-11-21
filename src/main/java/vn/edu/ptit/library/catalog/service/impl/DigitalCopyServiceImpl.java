package vn.edu.ptit.library.catalog.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;
import vn.edu.ptit.library.catalog.dto.DigitalCopyUploadResponse;
import vn.edu.ptit.library.catalog.dto.DigitalProcessingItemDto;
import vn.edu.ptit.library.catalog.dto.DigitalProcessingListResponse;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.indexing.repository.BackgroundJobRepository;
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
import vn.edu.ptit.library.recommendation.event.InteractionRecordedEvent;
import vn.edu.ptit.library.search.entity.SearchVectorConfig;
import vn.edu.ptit.library.search.repository.SearchVectorConfigRepository;
import vn.edu.ptit.library.user.entity.User;
import vn.edu.ptit.library.user.repository.UserRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final BackgroundJobRepository backgroundJobRepository;
    private final ApplicationEventPublisher eventPublisher;

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
                .fileName(file.getOriginalFilename())
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
                .fileName(digitalCopy.getFileName())
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
        Resource resource = storageService.loadAsResource(digitalCopy.getFilePath());
        UUID docId = digitalCopy.getDocument().getId();
        SecurityUtils.getCurrentUserDetails()
                .ifPresent(u -> eventPublisher.publishEvent(InteractionRecordedEvent.downloadOrRead(u.getId(), docId)));
        return resource;
    }

    @Override
    @Transactional(readOnly = true)
    public Resource readCurrentPdf(UUID documentId) {
        if (!documentRepository.existsById(documentId)) {
            throw new AppException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        DigitalCopy digitalCopy = digitalCopyRepository.findByDocumentIdAndIsCurrentTrue(documentId)
                .orElseThrow(() -> new AppException(ErrorCode.DIGITAL_COPY_NOT_FOUND, "Tài liệu này chưa có bản số để đọc trực tuyến"));
        Resource resource = storageService.loadAsResource(digitalCopy.getFilePath());
        SecurityUtils.getCurrentUserDetails()
                .ifPresent(u -> eventPublisher.publishEvent(InteractionRecordedEvent.downloadOrRead(u.getId(), documentId)));
        return resource;
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

    @Override
    @Transactional(readOnly = true)
    public DigitalProcessingListResponse listForProcessing(String status, String keyword, int page, int size) {
        List<String> statuses;
        if (StringUtils.hasText(status)) {
            try {
                statuses = List.of(DigitalProcessingStatus.valueOf(status.trim().toUpperCase()).name());
            } catch (IllegalArgumentException ex) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "Trạng thái xử lý không hợp lệ: " + status);
            }
        } else {
            statuses = Arrays.stream(DigitalProcessingStatus.values()).map(Enum::name).toList();
        }
        String pattern = "%" + (StringUtils.hasText(keyword) ? keyword.trim().toLowerCase() : "") + "%";
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<DigitalCopy> copies = digitalCopyRepository.searchForProcessing(statuses, pattern, pageable);

        // Tien trinh gan nhat cua tung ban so (id lon nhat)
        Map<UUID, BackgroundJob> latestJobs = new HashMap<>();
        List<UUID> ids = copies.getContent().stream().map(DigitalCopy::getId).toList();
        if (!ids.isEmpty()) {
            for (BackgroundJob job : backgroundJobRepository.findByJobTypeAndTargetIdIn(IndexingJobService.PDF_INDEXING_JOB_TYPE, ids)) {
                latestJobs.merge(job.getTargetId(), job, (a, b) -> a.getId() >= b.getId() ? a : b);
            }
        }

        Map<String, Long> counts = new LinkedHashMap<>();
        for (DigitalProcessingStatus s : DigitalProcessingStatus.values()) {
            counts.put(s.name(), 0L);
        }
        for (Object[] row : digitalCopyRepository.countByProcessingStatus()) {
            if (row[0] != null) {
                counts.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
            }
        }

        return DigitalProcessingListResponse.builder()
                .statusCounts(counts)
                .page(PageResponse.from(copies, copy -> toProcessingItem(copy, latestJobs.get(copy.getId()))))
                .build();
    }

    @Override
    @Transactional
    public DigitalProcessingItemDto reprocess(UUID digitalCopyId) {
        DigitalCopy copy = getById(digitalCopyId);
        String current = copy.getProcessingStatus();
        if (!DigitalProcessingStatus.FAILED.name().equals(current)
                && !DigitalProcessingStatus.LOW_QUALITY.name().equals(current)) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Chỉ chạy lại được bản số đang lỗi hoặc chất lượng thấp (hiện tại: " + current + ")");
        }
        Path absolute = storageService.resolveAbsolutePath(copy.getFilePath());
        if (!Files.exists(absolute)) {
            throw new AppException(ErrorCode.DIGITAL_COPY_NOT_FOUND,
                    "Không tìm thấy tệp PDF trên máy chủ, hãy tải lại tệp cho tài liệu này");
        }

        Long searchConfigId = searchVectorConfigRepository.findByIsActiveTrue()
                .map(SearchVectorConfig::getId)
                .orElse(null);
        copy.setProcessingStatus(DigitalProcessingStatus.PENDING.name());
        digitalCopyRepository.save(copy);
        BackgroundJob job = indexingJobService.enqueuePdfIndexingJob(
                copy.getId(), copy.getDocument().getId(), absolute.toString(), searchConfigId);
        return toProcessingItem(copy, job);
    }

    private DigitalProcessingItemDto toProcessingItem(DigitalCopy copy, BackgroundJob job) {
        Document doc = copy.getDocument();
        User uploader = copy.getUploadedBy();
        return DigitalProcessingItemDto.builder()
                .digitalCopyId(copy.getId())
                .documentId(doc != null ? doc.getId() : null)
                .documentTitle(doc != null ? doc.getTitle() : null)
                .fileName(copy.getFileName())
                .fileSize(copy.getFileSize())
                .version(copy.getVersion())
                .isCurrent(copy.getIsCurrent())
                .pageCount(copy.getPageCount())
                .extractionQuality(copy.getExtractionQuality())
                .processingStatus(copy.getProcessingStatus())
                .uploadedAt(copy.getCreatedAt())
                .uploadedBy(uploader != null ? uploader.getFullName() : null)
                .jobId(job != null ? job.getId() : null)
                .jobStatus(job != null ? job.getStatus() : null)
                .attempts(job != null ? job.getAttempts() : null)
                .errorMessage(job != null ? job.getErrorMessage() : null)
                .startedAt(job != null ? job.getStartedAt() : null)
                .finishedAt(job != null ? job.getFinishedAt() : null)
                .build();
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
