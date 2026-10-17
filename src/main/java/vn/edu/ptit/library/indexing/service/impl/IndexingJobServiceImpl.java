package vn.edu.ptit.library.indexing.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.library.common.enums.JobStatus;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;
import vn.edu.ptit.library.indexing.entity.BackgroundJob;
import vn.edu.ptit.library.indexing.repository.BackgroundJobRepository;
import vn.edu.ptit.library.indexing.service.IndexingJobService;
import vn.edu.ptit.library.integration.python.PythonWorkerClient;
import vn.edu.ptit.library.integration.python.dto.PdfIndexRequest;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingJobServiceImpl implements IndexingJobService {

    private final BackgroundJobRepository backgroundJobRepository;
    private final PythonWorkerClient pythonWorkerClient;

    @Override
    @Transactional
    public BackgroundJob enqueuePdfIndexingJob(UUID digitalCopyId, UUID documentId, String filePath, Long searchConfigId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("digitalCopyId", digitalCopyId.toString());
        payload.put("documentId", documentId.toString());
        payload.put("filePath", filePath);
        payload.put("searchConfigId", searchConfigId);

        BackgroundJob job = BackgroundJob.builder()
                .jobType("PDF_EXTRACTION_AND_INDEXING")
                .targetType("digital_copies")
                .targetId(digitalCopyId)
                .payload(payload)
                .status(JobStatus.QUEUED.name())
                .attempts(0)
                .build();

        BackgroundJob savedJob = backgroundJobRepository.save(job);

        // Goi bat dong bo sang Python Worker
        triggerWorkerAsync(savedJob.getId(), digitalCopyId, documentId, filePath, searchConfigId);

        return savedJob;
    }

    @Async("taskExecutor")
    public void triggerWorkerAsync(Long jobId, UUID digitalCopyId, UUID documentId, String filePath, Long searchConfigId) {
        try {
            PdfIndexRequest request = PdfIndexRequest.builder()
                    .backgroundJobId(jobId)
                    .digitalCopyId(digitalCopyId)
                    .documentId(documentId)
                    .filePath(filePath)
                    .searchConfigId(searchConfigId)
                    .build();

            pythonWorkerClient.requestPdfIndexing(request);
        } catch (Exception ex) {
            log.warn("Khong the thong bao ngay lap tuc cho Python Worker (Job ID: {}): {}", jobId, ex.getMessage());
        }
    }

    @Override
    @Async("taskExecutor")
    @Transactional
    public void enqueueSearchLogJob(UUID userId, Long departmentId, String query, Double maxScore, int resultCount) {
        try {
            Map<String, Object> payload = new HashMap<>();
            if (userId != null) {
                payload.put("userId", userId.toString());
            }
            if (departmentId != null) {
                payload.put("departmentId", departmentId);
            }
            payload.put("query", query);
            payload.put("maxScore", maxScore);
            payload.put("resultCount", resultCount);

            String jobType = (resultCount == 0 || (maxScore != null && maxScore < 0.25))
                    ? "LOG_ZERO_RESULT_QUERY"
                    : "LOG_USER_SEARCH";

            BackgroundJob job = BackgroundJob.builder()
                    .jobType(jobType)
                    .targetType("search_query")
                    .payload(payload)
                    .status(JobStatus.QUEUED.name())
                    .attempts(0)
                    .build();

            backgroundJobRepository.save(job);
        } catch (Exception ex) {
            log.warn("Loi khi ghi log tim kiem vao background_jobs: {}", ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BackgroundJob getJobStatus(Long jobId) {
        return backgroundJobRepository.findById(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tiến trình nền"));
    }
}
