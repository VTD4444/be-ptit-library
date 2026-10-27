package vn.edu.ptit.library.catalog.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.edu.ptit.library.catalog.dto.DocumentDetailDto;
import vn.edu.ptit.library.catalog.dto.DocumentFilterRequest;
import vn.edu.ptit.library.catalog.dto.DocumentRequestDto;
import vn.edu.ptit.library.catalog.dto.DocumentSummaryDto;
import vn.edu.ptit.library.catalog.entity.Author;
import vn.edu.ptit.library.catalog.entity.DigitalCopy;
import vn.edu.ptit.library.catalog.entity.Document;
import vn.edu.ptit.library.catalog.entity.DocumentAuthor;
import vn.edu.ptit.library.catalog.entity.DocumentDepartment;
import vn.edu.ptit.library.catalog.entity.PhysicalCopy;
import vn.edu.ptit.library.catalog.repository.AuthorRepository;
import vn.edu.ptit.library.catalog.repository.DigitalCopyRepository;
import vn.edu.ptit.library.catalog.repository.DocumentAuthorRepository;
import vn.edu.ptit.library.catalog.repository.DocumentDepartmentRepository;
import vn.edu.ptit.library.catalog.repository.DocumentRepository;
import vn.edu.ptit.library.catalog.repository.DocumentSpecification;
import vn.edu.ptit.library.catalog.repository.PhysicalCopyRepository;
import vn.edu.ptit.library.catalog.repository.ReviewRepository;
import vn.edu.ptit.library.catalog.service.CatalogService;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;
import vn.edu.ptit.library.common.enums.PhysicalCopyStatus;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;
import vn.edu.ptit.library.user.entity.Department;
import vn.edu.ptit.library.user.repository.DepartmentRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogServiceImpl implements CatalogService {

    private static final String COVER_URL_KEY = "coverUrl";
    private static final String AVAILABLE = PhysicalCopyStatus.AVAILABLE.name();

    private final DocumentRepository documentRepository;
    private final AuthorRepository authorRepository;
    private final DocumentAuthorRepository documentAuthorRepository;
    private final DocumentDepartmentRepository documentDepartmentRepository;
    private final DepartmentRepository departmentRepository;
    private final PhysicalCopyRepository physicalCopyRepository;
    private final DigitalCopyRepository digitalCopyRepository;
    private final ReviewRepository reviewRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DocumentSummaryDto> searchDocuments(DocumentFilterRequest filter, PaginationRequest request) {
        Page<Document> page = documentRepository.findAll(DocumentSpecification.filter(filter), request.toPageable());
        return PageResponse.from(page, this::toSummaryDto);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDetailDto getDocumentDetails(UUID id) {
        Document doc = getDocumentEntityOrThrow(id);
        List<DocumentDepartment> departments = documentDepartmentRepository.findByDocumentId(id);
        Optional<DigitalCopy> digital = digitalCopyRepository.findByDocumentIdAndIsCurrentTrue(id);

        return DocumentDetailDto.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .isbn(doc.getIsbn())
                .authors(getAuthorNames(id))
                .publisher(doc.getPublisher())
                .publicationYear(doc.getPublicationYear())
                .language(doc.getLanguage())
                .docType(doc.getDocType())
                .classificationNumber(doc.getClassificationNumber())
                .abstractText(doc.getAbstractText())
                .coverUrl(getCoverUrl(doc))
                .metadata(doc.getMetadata())
                .departmentIds(departments.stream().map(d -> d.getDepartment().getId()).toList())
                .departmentNames(departments.stream().map(d -> d.getDepartment().getName()).toList())
                .totalPhysicalCopies(physicalCopyRepository.countByDocumentId(id))
                .availablePhysicalCopies(physicalCopyRepository.countByDocumentIdAndStatus(id, AVAILABLE))
                .hasDigitalCopy(digital.isPresent())
                .digitalCopyId(digital.map(DigitalCopy::getId).orElse(null))
                .digitalProcessingStatus(digital.map(DigitalCopy::getProcessingStatus).orElse(null))
                .pageCount(digital.map(DigitalCopy::getPageCount).orElse(null))
                .averageRating(reviewRepository.getAverageRatingForDocument(id))
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Document getDocumentEntityOrThrow(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    @Override
    @Transactional
    public UUID createDocument(DocumentRequestDto request) {
        checkDuplicateIsbn(request.getIsbn(), null);

        Document doc = new Document();
        applyRequest(doc, request);
        Document saved = documentRepository.save(doc);

        saveAuthors(saved, request.getAuthors());
        saveDepartments(saved, request.getDepartmentIds());

        return saved.getId();
    }

    @Override
    @Transactional
    public UUID updateDocument(UUID id, DocumentRequestDto request) {
        Document doc = getDocumentEntityOrThrow(id);
        checkDuplicateIsbn(request.getIsbn(), id);

        applyRequest(doc, request);
        documentRepository.save(doc);

        saveAuthors(doc, request.getAuthors());
        saveDepartments(doc, request.getDepartmentIds());

        return doc.getId();
    }

    @Override
    @Transactional
    public void deleteDocument(UUID id) {
        Document doc = getDocumentEntityOrThrow(id);
        List<PhysicalCopy> copies = physicalCopyRepository.findByDocumentId(id);

        boolean hasBorrowedCopy = copies.stream()
                .anyMatch(c -> PhysicalCopyStatus.BORROWED.name().equals(c.getStatus()));
        if (hasBorrowedCopy) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Đầu sách còn bản đang được mượn, không thể xóa");
        }

        copies.forEach(PhysicalCopy::markDeleted);
        physicalCopyRepository.saveAll(copies);

        doc.markDeleted();
        documentRepository.save(doc);
    }

    private void applyRequest(Document doc, DocumentRequestDto request) {
        doc.setTitle(request.getTitle().trim());
        doc.setIsbn(trimToNull(request.getIsbn()));
        doc.setPublisher(request.getPublisher());
        doc.setPublicationYear(request.getPublicationYear());
        doc.setAbstractText(request.getAbstractText());
        doc.setDocType(request.getDocType());
        doc.setClassificationNumber(request.getClassificationNumber());
        doc.setLanguage(request.getLanguage());

        Map<String, Object> metadata = doc.getMetadata() != null
                ? new HashMap<>(doc.getMetadata())
                : new HashMap<>();

        if (StringUtils.hasText(request.getCoverUrl())) {
            metadata.put(COVER_URL_KEY, request.getCoverUrl().trim());
        } else {
            metadata.remove(COVER_URL_KEY);
        }
        doc.setMetadata(metadata);
    }

    private void checkDuplicateIsbn(String isbn, UUID currentDocumentId) {
        if (!StringUtils.hasText(isbn)) {
            return;
        }

        documentRepository.findByIsbn(isbn.trim())
                .filter(existing -> !existing.getId().equals(currentDocumentId))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.DUPLICATE_RESOURCE, "ISBN đã tồn tại: " + isbn);
                });
    }

    private void saveAuthors(Document doc, List<String> authorNames) {
        documentAuthorRepository.deleteByDocumentId(doc.getId());
        documentAuthorRepository.flush();

        if (authorNames == null) {
            return;
        }

        List<String> names = authorNames.stream()
                .filter(StringUtils::hasText)
                .map(name -> name.trim().replaceAll("\\s+", " "))
                .distinct()
                .toList();

        int order = 1;
        for (String name : names) {
            Author author = authorRepository.findFirstByNameIgnoreCase(name)
                    .orElseGet(() -> authorRepository.save(Author.builder().name(name).build()));

            documentAuthorRepository.save(DocumentAuthor.builder()
                    .document(doc)
                    .author(author)
                    .authorOrder(order++)
                    .build());
        }
    }

    private void saveDepartments(Document doc, List<Long> departmentIds) {
        documentDepartmentRepository.deleteByDocumentId(doc.getId());
        documentDepartmentRepository.flush();

        if (departmentIds == null) {
            return;
        }

        for (Long departmentId : departmentIds.stream().distinct().toList()) {
            Department department = departmentRepository.findById(departmentId)
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                            "Không tìm thấy ngành học với id: " + departmentId));

            documentDepartmentRepository.save(DocumentDepartment.builder()
                    .document(doc)
                    .department(department)
                    .build());
        }
    }

    private DocumentSummaryDto toSummaryDto(Document doc) {
        UUID id = doc.getId();

        return DocumentSummaryDto.builder()
                .id(id)
                .title(doc.getTitle())
                .authors(getAuthorNames(id))
                .coverUrl(getCoverUrl(doc))
                .abstractText(doc.getAbstractText())
                .docType(doc.getDocType())
                .publicationYear(doc.getPublicationYear())
                .totalPhysicalCopies(physicalCopyRepository.countByDocumentId(id))
                .availablePhysicalCopies(physicalCopyRepository.countByDocumentIdAndStatus(id, AVAILABLE))
                .hasDigitalCopy(digitalCopyRepository.findByDocumentIdAndIsCurrentTrue(id).isPresent())
                .build();
    }

    private List<String> getAuthorNames(UUID documentId) {
        return documentAuthorRepository.findByDocumentIdOrderByAuthorOrderAsc(documentId)
                .stream()
                .map(DocumentAuthor::getAuthor)
                .filter(a -> a != null && a.getName() != null)
                .map(Author::getName)
                .toList();
    }

    private String getCoverUrl(Document doc) {
        if (doc.getMetadata() == null) {
            return null;
        }

        Object cover = doc.getMetadata().get(COVER_URL_KEY);
        return cover != null ? cover.toString() : null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
