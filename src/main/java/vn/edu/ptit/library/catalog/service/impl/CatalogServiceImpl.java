package vn.edu.ptit.library.catalog.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
import vn.edu.ptit.library.common.util.SecurityUtils;
import vn.edu.ptit.library.recommendation.event.InteractionRecordedEvent;
import vn.edu.ptit.library.user.entity.Department;
import vn.edu.ptit.library.user.repository.DepartmentRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DocumentSummaryDto> searchDocuments(DocumentFilterRequest filter, PaginationRequest request) {
        Page<Document> page = documentRepository.findAll(DocumentSpecification.filter(filter), request.toPageable());
        List<Document> documents = page.getContent();
        if (documents.isEmpty()) {
            return PageResponse.from(page, doc -> null);
        }

        List<UUID> documentIds = documents.stream()
                .map(Document::getId)
                .toList();

        Map<UUID, List<String>> authorsByDoc = new HashMap<>();
        for (DocumentAuthor da : documentAuthorRepository.findByDocumentIdInWithAuthor(documentIds)) {
            if (da.getAuthor() != null && da.getAuthor().getName() != null) {
                authorsByDoc.computeIfAbsent(da.getId().getDocumentId(), k -> new ArrayList<>())
                        .add(da.getAuthor().getName());
            }
        }

        Map<UUID, long[]> copiesByDoc = new HashMap<>();
        for (Object[] row : physicalCopyRepository.countCopiesByDocumentIds(documentIds)) {
            UUID docId = (UUID) row[0];
            long total = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            long available = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            copiesByDoc.put(docId, new long[]{total, available});
        }

        Set<UUID> docsWithDigital = new HashSet<>(
                digitalCopyRepository.findDocumentIdsWithCurrentDigitalCopy(documentIds));

        return PageResponse.from(page, doc -> {
            UUID id = doc.getId();
            long[] counts = copiesByDoc.getOrDefault(id, new long[]{0L, 0L});
            return DocumentSummaryDto.builder()
                    .id(id)
                    .title(doc.getTitle())
                    .authors(authorsByDoc.getOrDefault(id, List.of()))
                    .coverUrl(getCoverUrl(doc))
                    .abstractText(doc.getAbstractText())
                    .docType(doc.getDocType())
                    .publicationYear(doc.getPublicationYear())
                    .totalPhysicalCopies(counts[0])
                    .availablePhysicalCopies(counts[1])
                    .hasDigitalCopy(docsWithDigital.contains(id))
                    .build();
        });
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDetailDto getDocumentDetails(UUID id) {
        Document doc = getDocumentEntityOrThrow(id);
        List<DocumentDepartment> departments = documentDepartmentRepository.findByDocumentIdWithDepartment(id);
        Optional<DigitalCopy> digital = digitalCopyRepository.findByDocumentIdAndIsCurrentTrue(id);
        List<PhysicalCopy> copies = physicalCopyRepository.findByDocumentId(id);
        List<vn.edu.ptit.library.catalog.dto.PhysicalCopyDto> physicalCopyDtos = copies.stream()
                .sorted(java.util.Comparator.comparing(
                        PhysicalCopy::getBarcode,
                        java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(copy -> vn.edu.ptit.library.catalog.dto.PhysicalCopyDto.builder()
                        .id(copy.getId())
                        .documentId(id)
                        .barcode(copy.getBarcode())
                        .callNumber(copy.getCallNumber())
                        .shelfLocation(copy.getShelfLocation())
                        .status(copy.getStatus())
                        .condition(copy.getCondition())
                        .accessionDate(copy.getAccessionDate())
                        .lastInventoryDate(copy.getLastInventoryDate())
                        .build())
                .toList();
        long availablePhysicalCopies = copies.stream()
                .filter(copy -> AVAILABLE.equalsIgnoreCase(copy.getStatus()))
                .count();

        SecurityUtils.getCurrentUserDetails()
                .ifPresent(u -> eventPublisher.publishEvent(InteractionRecordedEvent.viewDetail(u.getId(), doc.getId())));

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
                .origin(doc.getOrigin())
                .physicalDescription(doc.getPhysicalDescription())
                .metadata(doc.getMetadata())
                .departmentIds(departments.stream().map(d -> d.getDepartment().getId()).toList())
                .departmentNames(departments.stream().map(d -> d.getDepartment().getName()).toList())
                .totalPhysicalCopies(copies.size())
                .availablePhysicalCopies(availablePhysicalCopies)
                .physicalCopies(physicalCopyDtos)
                .hasDigitalCopy(digital.isPresent())
                .digitalCopyId(digital.map(DigitalCopy::getId).orElse(null))
                .digitalFileName(digital.map(DigitalCopy::getFileName).orElse(null))
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
        doc.setCoverUrl(trimToNull(request.getCoverUrl()));
        doc.setOrigin(trimToNull(request.getOrigin()));
        doc.setPhysicalDescription(trimToNull(request.getPhysicalDescription()));

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

    private List<String> getAuthorNames(UUID documentId) {
        return documentAuthorRepository.findByDocumentIdInWithAuthor(List.of(documentId))
                .stream()
                .map(DocumentAuthor::getAuthor)
                .filter(a -> a != null && a.getName() != null)
                .map(Author::getName)
                .toList();
    }

    private String getCoverUrl(Document doc) {
        if (StringUtils.hasText(doc.getCoverUrl())) {
            return doc.getCoverUrl();
        }
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
