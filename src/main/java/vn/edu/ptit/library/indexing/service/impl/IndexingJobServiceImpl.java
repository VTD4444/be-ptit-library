package vn.edu.ptit.library.indexing.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.edu.ptit.library.catalog.repository.DigitalCopyRepository;
import vn.edu.ptit.library.common.enums.DigitalProcessingStatus;
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
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingJobServiceImpl implements IndexingJobService {

    private final BackgroundJobRepository backgroundJobRepository;
    private final PythonWorkerClient pythonWorkerClient;
    private final DigitalCopyRepository digitalCopyRepository;
    private final Executor taskExecutor;

    @Override
    @Transactional
    public BackgroundJob enqueuePdfIndexingJob(UUID digitalCopyId, UUID documentId, String filePath, Long searchConfigId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("digitalCopyId", digitalCopyId.toString());
        payload.put("documentId", documentId.toString());
        payload.put("filePath", filePath);
        payload.put("searchConfigId", searchConfigId);

        BackgroundJob job = BackgroundJob.builder()
                .jobType(PDF_INDEXING_JOB_TYPE)
                .targetType("digital_copies")
                .targetId(digitalCopyId)
                .payload(payload)
                .status(JobStatus.QUEUED.name())
                .attempts(0)
                .build();

        BackgroundJob savedJob = backgroundJobRepository.save(job);

        PdfIndexRequest request = PdfIndexRequest.builder()
                .backgroundJobId(savedJob.getId())
                .digitalCopyId(digitalCopyId)
                .documentId(documentId)
                .filePath(filePath)
                .searchConfigId(searchConfigId)
                .build();

        // Python Worker doc job/digital copy tu DB nen chi goi sang sau khi transaction da commit
        Runnable dispatch = () -> taskExecutor.execute(() -> dispatchToWorker(request));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else {
            dispatch.run();
        }

        return savedJob;
    }

    private void dispatchToWorker(PdfIndexRequest request) {
        try {
            pythonWorkerClient.requestPdfIndexing(request);
        } catch (Exception ex) {
            log.warn("Khong the gui job boc tach sang Python Worker (Job ID: {}): {}", request.getBackgroundJobId(), ex.getMessage());
            markDispatchFailed(request, ex.getMessage());
        }
    }

    private void markDispatchFailed(PdfIndexRequest request, String reason) {
        backgroundJobRepository.findById(request.getBackgroundJobId()).ifPresent(job -> {
            job.setStatus(JobStatus.FAILED.name());
            job.setErrorMessage("Không gửi được yêu cầu tới Python Worker: " + reason);
            job.setFinishedAt(OffsetDateTime.now());
            backgroundJobRepository.save(job);
        });
        digitalCopyRepository.findById(request.getDigitalCopyId()).ifPresent(copy -> {
            copy.setProcessingStatus(DigitalProcessingStatus.FAILED.name());
            digitalCopyRepository.save(copy);
        });
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
