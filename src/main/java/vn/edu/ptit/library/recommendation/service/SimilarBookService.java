package vn.edu.ptit.library.recommendation.service;

import vn.edu.ptit.library.integration.python.dto.SimilarRebuildResponse;
import vn.edu.ptit.library.recommendation.dto.SimilarDocumentDto;

import java.util.List;
import java.util.UUID;

public interface SimilarBookService {

    List<SimilarDocumentDto> getSimilarBooks(UUID documentId, int limit);

    SimilarRebuildResponse rebuildSimilarBooks();
}
