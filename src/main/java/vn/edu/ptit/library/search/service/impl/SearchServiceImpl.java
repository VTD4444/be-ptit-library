package vn.edu.ptit.library.search.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.library.catalog.entity.PhysicalCopy;
import vn.edu.ptit.library.catalog.repository.PhysicalCopyRepository;
import vn.edu.ptit.library.common.util.SecurityUtils;
import vn.edu.ptit.library.indexing.service.IndexingJobService;
import vn.edu.ptit.library.integration.python.PythonWorkerClient;
import vn.edu.ptit.library.integration.python.dto.EmbeddingResponse;
import vn.edu.ptit.library.search.dto.ContentSearchRequest;
import vn.edu.ptit.library.search.dto.ContentSearchResultDto;
import vn.edu.ptit.library.search.entity.SearchVectorConfig;
import vn.edu.ptit.library.search.repository.SearchDocumentChunkRepository;
import vn.edu.ptit.library.search.repository.SearchVectorConfigRepository;
import vn.edu.ptit.library.search.repository.projection.ChunkSearchResultProjection;
import vn.edu.ptit.library.search.service.SearchService;
import vn.edu.ptit.library.user.entity.User;
import vn.edu.ptit.library.user.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final SearchDocumentChunkRepository chunkRepository;
    private final SearchVectorConfigRepository configRepository;
    private final PhysicalCopyRepository physicalCopyRepository;
    private final PythonWorkerClient pythonWorkerClient;
    private final IndexingJobService indexingJobService;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ContentSearchResultDto> searchContent(ContentSearchRequest request) {
        SearchVectorConfig activeConfig = configRepository.findByIsActiveTrue().orElse(null);
        String modelName = activeConfig != null ? activeConfig.getEmbeddingModel() : "bkai-foundation-models/vietnamese-bi-encoder";
        Long configId = activeConfig != null ? activeConfig.getId() : null;

        List<ChunkSearchResultProjection> rawResults = new ArrayList<>();

        if ("BM25".equalsIgnoreCase(request.getSearchType())) {
            rawResults = chunkRepository.searchByFullText(request.getQuery(), request.getLimit());
        } else {
            // Sinh vector embedding tu Python Worker va thuc hien cosine similarity tren pgvector
            try {
                EmbeddingResponse embResp = pythonWorkerClient.generateEmbedding(request.getQuery(), modelName);
                if (embResp != null) {
                    rawResults = chunkRepository.searchByCosineSimilarity(embResp.toPgVectorString(), configId, request.getLimit());
                }
            } catch (Exception ex) {
                log.warn("Loi khi goi vector search, fallback sang Full-Text Search: {}", ex.getMessage());
                rawResults = chunkRepository.searchByFullText(request.getQuery(), request.getLimit());
            }
        }

        // Tinh maxScore de ghi nhan lich su va phat hien truy van khong ket qua (Muc 2.4 & 2.5)
        Double maxScore = rawResults.isEmpty() ? 0.0 : rawResults.get(0).getSemanticScore();

        UUID currentUserId = SecurityUtils.getCurrentUserDetails().map(u -> u.getId()).orElse(null);
        Long departmentId = null;
        if (currentUserId != null) {
            departmentId = userRepository.findById(currentUserId)
                    .map(User::getDepartment)
                    .map(d -> d.getId())
                    .orElse(null);
        }

        indexingJobService.enqueueSearchLogJob(currentUserId, departmentId, request.getQuery(), maxScore, rawResults.size());

        return rawResults.stream().map(r -> {
            List<PhysicalCopy> copies = physicalCopyRepository.findAvailableCopiesByDocumentId(r.getDocumentId());
            String shelfLocation = copies.isEmpty() ? null : copies.get(0).getShelfLocation();

            return ContentSearchResultDto.builder()
                    .documentId(r.getDocumentId())
                    .documentTitle(r.getTitle())
                    .chunkId(r.getId())
                    .chunkText(r.getChunkText())
                    .pageNumber(r.getPageNumber())
                    .chapter(r.getChapter())
                    .section(r.getSection())
                    .relevanceScore(r.getSemanticScore())
                    .availablePhysicalCopies(copies.size())
                    .shelfLocation(shelfLocation)
                    .build();
        }).toList();
    }
}
