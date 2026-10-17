package vn.edu.ptit.library.indexing.service;

import vn.edu.ptit.library.indexing.entity.BackgroundJob;

import java.util.UUID;

public interface IndexingJobService {

    BackgroundJob enqueuePdfIndexingJob(UUID digitalCopyId, UUID documentId, String filePath, Long searchConfigId);

    void enqueueSearchLogJob(UUID userId, Long departmentId, String query, Double maxScore, int resultCount);

    BackgroundJob getJobStatus(Long jobId);
}
