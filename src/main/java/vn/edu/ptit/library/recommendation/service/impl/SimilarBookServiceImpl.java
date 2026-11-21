package vn.edu.ptit.library.recommendation.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.edu.ptit.library.catalog.entity.Author;
import vn.edu.ptit.library.catalog.entity.Document;
import vn.edu.ptit.library.catalog.entity.DocumentAuthor;
import vn.edu.ptit.library.catalog.repository.DigitalCopyRepository;
import vn.edu.ptit.library.catalog.repository.DocumentAuthorRepository;
import vn.edu.ptit.library.catalog.repository.DocumentRepository;
import vn.edu.ptit.library.catalog.repository.PhysicalCopyRepository;
import vn.edu.ptit.library.common.enums.PhysicalCopyStatus;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;
import vn.edu.ptit.library.integration.python.PythonWorkerClient;
import vn.edu.ptit.library.integration.python.dto.SimilarRebuildRequest;
import vn.edu.ptit.library.integration.python.dto.SimilarRebuildResponse;
import vn.edu.ptit.library.recommendation.dto.SimilarDocumentDto;
import vn.edu.ptit.library.recommendation.service.SimilarBookService;
import vn.edu.ptit.library.search.entity.DocumentSimilarity;
import vn.edu.ptit.library.search.repository.DocumentSimilarityRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SimilarBookServiceImpl implements SimilarBookService {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 20;
    private static final String COVER_URL_KEY = "coverUrl";
    private static final String AVAILABLE_STATUS = PhysicalCopyStatus.AVAILABLE.name();

    private final DocumentRepository documentRepository;
    private final DocumentSimilarityRepository documentSimilarityRepository;
    private final DocumentAuthorRepository documentAuthorRepository;
    private final PhysicalCopyRepository physicalCopyRepository;
    private final DigitalCopyRepository digitalCopyRepository;
    private final PythonWorkerClient pythonWorkerClient;

    @Value("${app.recommendation.similar-min-score:0.05}")
    private double minScore;

    @Value("${app.recommendation.similar-model-version:tfidf-v1}")
    private String modelVersion;

    @Value("${app.recommendation.similar-top-k:20}")
    private int topK;

    @Override
    @Transactional(readOnly = true)
    public List<SimilarDocumentDto> getSimilarBooks(UUID documentId, int limit) {
        if (!documentRepository.existsById(documentId)) {
            throw new AppException(ErrorCode.DOCUMENT_NOT_FOUND, "Không tìm thấy tài liệu với id: " + documentId);
        }

        int safeLimit = Math.max(MIN_LIMIT, Math.min(limit, MAX_LIMIT));
        List<DocumentSimilarity> rows = documentSimilarityRepository.findSimilarDocuments(
                documentId, modelVersion, minScore, PageRequest.of(0, safeLimit));

        return rows.stream()
                .map(this::toSimilarDto)
                .toList();
    }

    @Override
    public SimilarRebuildResponse rebuildSimilarBooks() {
        SimilarRebuildRequest request = SimilarRebuildRequest.builder()
                .modelVersion(modelVersion)
                .minScore(minScore)
                .topK(topK)
                .build();
        return pythonWorkerClient.rebuildSimilarBooks(request);
    }

    private SimilarDocumentDto toSimilarDto(DocumentSimilarity similarity) {
        Document target = similarity.getDocumentB();
        UUID targetId = target.getId();

        return SimilarDocumentDto.builder()
                .documentId(targetId)
                .title(target.getTitle())
                .authors(getAuthorNames(targetId))
                .coverUrl(resolveCoverUrl(target))
                .abstractText(target.getAbstractText())
                .docType(target.getDocType())
                .classificationNumber(target.getClassificationNumber())
                .publicationYear(target.getPublicationYear())
                .totalPhysicalCopies(physicalCopyRepository.countByDocumentId(targetId))
                .availablePhysicalCopies(physicalCopyRepository.countByDocumentIdAndStatus(targetId, AVAILABLE_STATUS))
                .hasDigitalCopy(digitalCopyRepository.findByDocumentIdAndIsCurrentTrue(targetId).isPresent())
                .similarityScore(similarity.getSimScore() != null ? similarity.getSimScore() : 0.0)
                .modelVersion(similarity.getId() != null ? similarity.getId().getModelVersion() : modelVersion)
                .source(similarity.getSource())
                .build();
    }

    private List<String> getAuthorNames(UUID documentId) {
        return documentAuthorRepository.findByDocumentIdOrderByAuthorOrderAsc(documentId)
                .stream()
                .map(DocumentAuthor::getAuthor)
                .filter(author -> author != null && author.getName() != null)
                .map(Author::getName)
                .toList();
    }

    private String resolveCoverUrl(Document doc) {
        if (StringUtils.hasText(doc.getCoverUrl())) {
            return doc.getCoverUrl();
        }
        if (doc.getMetadata() == null) {
            return null;
        }
        Object cover = doc.getMetadata().get(COVER_URL_KEY);
        return cover != null ? cover.toString() : null;
    }
}
