package vn.edu.ptit.library.indexing.service;

import vn.edu.ptit.library.indexing.entity.BackgroundJob;

import java.util.UUID;

public interface IndexingJobService {

    String PDF_INDEXING_JOB_TYPE = "PDF_EXTRACTION_AND_INDEXING";

    BackgroundJob enqueuePdfIndexingJob(UUID digitalCopyId, UUID documentId, String filePath, Long searchConfigId);

    void enqueueSearchLogJob(UUID userId, Long departmentId, String query, Double maxScore, int resultCount);

    BackgroundJob getJobStatus(Long jobId);
}
