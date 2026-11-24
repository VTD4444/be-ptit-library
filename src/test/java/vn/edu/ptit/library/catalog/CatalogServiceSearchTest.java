package vn.edu.ptit.library.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import vn.edu.ptit.library.catalog.dto.DocumentFilterRequest;
import vn.edu.ptit.library.catalog.dto.DocumentSummaryDto;
import vn.edu.ptit.library.catalog.entity.Author;
import vn.edu.ptit.library.catalog.entity.Document;
import vn.edu.ptit.library.catalog.entity.DocumentAuthor;
import vn.edu.ptit.library.catalog.entity.DocumentAuthorId;
import vn.edu.ptit.library.catalog.repository.AuthorRepository;
import vn.edu.ptit.library.catalog.repository.DigitalCopyRepository;
import vn.edu.ptit.library.catalog.repository.DocumentAuthorRepository;
import vn.edu.ptit.library.catalog.repository.DocumentDepartmentRepository;
import vn.edu.ptit.library.catalog.repository.DocumentRepository;
import vn.edu.ptit.library.catalog.repository.PhysicalCopyRepository;
import vn.edu.ptit.library.catalog.repository.ReviewRepository;
import vn.edu.ptit.library.catalog.service.impl.CatalogServiceImpl;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;
import vn.edu.ptit.library.user.repository.DepartmentRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogServiceSearchTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private AuthorRepository authorRepository;
    @Mock
    private DocumentAuthorRepository documentAuthorRepository;
    @Mock
    private DocumentDepartmentRepository documentDepartmentRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private PhysicalCopyRepository physicalCopyRepository;
    @Mock
    private DigitalCopyRepository digitalCopyRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CatalogServiceImpl catalogService;

    @Test
    @SuppressWarnings("unchecked")
    void searchDocuments_shouldBatchLoadAuthorsPhysicalAndDigitalCopiesWithoutNPlusOne() {
        UUID doc1Id = UUID.randomUUID();
        UUID doc2Id = UUID.randomUUID();

        Document doc1 = Document.builder().id(doc1Id).title("Mang may tinh").docType("textbook").publicationYear(2023).build();
        Document doc2 = Document.builder().id(doc2Id).title("Tri tue nhan tao").docType("reference").publicationYear(2024).build();

        Author author1 = Author.builder().id(1L).name("Nguyen Van A").build();
        DocumentAuthor da1 = DocumentAuthor.builder()
                .id(new DocumentAuthorId(doc1Id, 1L))
                .document(doc1)
                .author(author1)
                .authorOrder(1)
                .build();

        when(documentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(doc1, doc2)));
        when(documentAuthorRepository.findByDocumentIdInWithAuthor(List.of(doc1Id, doc2Id)))
                .thenReturn(List.of(da1));
        when(physicalCopyRepository.countCopiesByDocumentIds(List.of(doc1Id, doc2Id)))
                .thenReturn(List.<Object[]>of(new Object[]{doc1Id, 5L, 3L}));
        when(digitalCopyRepository.findDocumentIdsWithCurrentDigitalCopy(List.of(doc1Id, doc2Id)))
                .thenReturn(List.of(doc2Id));

        PaginationRequest pagination = new PaginationRequest();
        pagination.setPage(0);
        pagination.setSize(20);

        PageResponse<DocumentSummaryDto> result = catalogService.searchDocuments(new DocumentFilterRequest(), pagination);

        assertEquals(2, result.getItems().size());

        DocumentSummaryDto dto1 = result.getItems().get(0);
        assertEquals(List.of("Nguyen Van A"), dto1.getAuthors());
        assertEquals(5L, dto1.getTotalPhysicalCopies());
        assertEquals(3L, dto1.getAvailablePhysicalCopies());
        assertFalse(dto1.isHasDigitalCopy());

        DocumentSummaryDto dto2 = result.getItems().get(1);
        assertEquals(List.of(), dto2.getAuthors());
        assertEquals(0L, dto2.getTotalPhysicalCopies());
        assertEquals(0L, dto2.getAvailablePhysicalCopies());
        assertTrue(dto2.isHasDigitalCopy());

        verify(documentAuthorRepository, times(1)).findByDocumentIdInWithAuthor(List.of(doc1Id, doc2Id));
        verify(physicalCopyRepository, times(1)).countCopiesByDocumentIds(List.of(doc1Id, doc2Id));
        verify(digitalCopyRepository, times(1)).findDocumentIdsWithCurrentDigitalCopy(List.of(doc1Id, doc2Id));

        verify(documentAuthorRepository, never()).findByDocumentIdOrderByAuthorOrderAsc(any());
        verify(physicalCopyRepository, never()).countByDocumentId(any());
        verify(physicalCopyRepository, never()).countByDocumentIdAndStatus(any(), any());
        verify(digitalCopyRepository, never()).findByDocumentIdAndIsCurrentTrue(any());
    }
}
