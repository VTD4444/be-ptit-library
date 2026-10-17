package vn.edu.ptit.library.catalog.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.edu.ptit.library.catalog.dto.DocumentSummaryDto;
import vn.edu.ptit.library.catalog.entity.Document;
import vn.edu.ptit.library.catalog.entity.DocumentAuthor;
import vn.edu.ptit.library.catalog.repository.DigitalCopyRepository;
import vn.edu.ptit.library.catalog.repository.DocumentAuthorRepository;
import vn.edu.ptit.library.catalog.repository.DocumentRepository;
import vn.edu.ptit.library.catalog.repository.PhysicalCopyRepository;
import vn.edu.ptit.library.catalog.repository.ReviewRepository;
import vn.edu.ptit.library.catalog.service.CatalogService;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogServiceImpl implements CatalogService {

    private final DocumentRepository documentRepository;
    private final DocumentAuthorRepository documentAuthorRepository;
    private final PhysicalCopyRepository physicalCopyRepository;
    private final DigitalCopyRepository digitalCopyRepository;
    private final ReviewRepository reviewRepository;

    @Override
    @Transactional(readOnly = true)
    public DocumentSummaryDto getDocumentDetails(UUID id) {
        Document document = getDocumentEntityOrThrow(id);
        return toSummaryDto(document);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DocumentSummaryDto> searchDocuments(String keyword, PaginationRequest request) {
        Page<Document> page;
        if (StringUtils.hasText(keyword)) {
            page = documentRepository.searchByTitle(keyword.trim(), request.toPageable());
        } else {
            page = documentRepository.findAll(request.toPageable());
        }
        return PageResponse.from(page, this::toSummaryDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Document getDocumentEntityOrThrow(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    private DocumentSummaryDto toSummaryDto(Document doc) {
        List<String> authors = documentAuthorRepository.findByDocumentIdOrderByAuthorOrderAsc(doc.getId())
                .stream()
                .map(DocumentAuthor::getAuthor)
                .filter(a -> a != null && a.getName() != null)
                .map(a -> a.getName())
                .toList();

        long availableCopies = physicalCopyRepository.countByDocumentIdAndStatus(doc.getId(), "AVAILABLE");
        long totalCopies = physicalCopyRepository.findByDocumentId(doc.getId()).size();
        boolean hasDigital = digitalCopyRepository.findByDocumentIdAndIsCurrentTrue(doc.getId()).isPresent();
        Double avgRating = reviewRepository.getAverageRatingForDocument(doc.getId());

        return DocumentSummaryDto.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .isbn(doc.getIsbn())
                .publisher(doc.getPublisher())
                .publicationYear(doc.getPublicationYear())
                .language(doc.getLanguage())
                .docType(doc.getDocType())
                .classificationNumber(doc.getClassificationNumber())
                .authors(authors)
                .totalPhysicalCopies(totalCopies)
                .availablePhysicalCopies(availableCopies)
                .hasDigitalCopy(hasDigital)
                .averageRating(avgRating)
                .build();
    }
}
