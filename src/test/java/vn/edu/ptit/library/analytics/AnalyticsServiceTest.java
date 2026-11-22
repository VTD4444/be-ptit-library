package vn.edu.ptit.library.analytics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import vn.edu.ptit.library.analytics.dto.AcquisitionSuggestionDto;
import vn.edu.ptit.library.analytics.dto.LibraryOverviewStatsDto;
import vn.edu.ptit.library.analytics.dto.PopularDocumentDto;
import vn.edu.ptit.library.analytics.entity.AcquisitionSuggestion;
import vn.edu.ptit.library.analytics.repository.AcquisitionSuggestionRepository;
import vn.edu.ptit.library.analytics.service.impl.AnalyticsServiceImpl;
import vn.edu.ptit.library.catalog.entity.Author;
import vn.edu.ptit.library.catalog.entity.Document;
import vn.edu.ptit.library.catalog.entity.DocumentAuthor;
import vn.edu.ptit.library.catalog.entity.DocumentAuthorId;
import vn.edu.ptit.library.catalog.repository.DigitalCopyRepository;
import vn.edu.ptit.library.catalog.repository.DocumentAuthorRepository;
import vn.edu.ptit.library.catalog.repository.DocumentRepository;
import vn.edu.ptit.library.catalog.repository.PhysicalCopyRepository;
import vn.edu.ptit.library.circulation.repository.CirculationRepository;
import vn.edu.ptit.library.circulation.repository.HoldQueueRepository;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;
import vn.edu.ptit.library.indexing.repository.BackgroundJobRepository;
import vn.edu.ptit.library.user.entity.Department;
import vn.edu.ptit.library.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private PhysicalCopyRepository physicalCopyRepository;
    @Mock private DigitalCopyRepository digitalCopyRepository;
    @Mock private DocumentAuthorRepository documentAuthorRepository;
    @Mock private UserRepository userRepository;
    @Mock private CirculationRepository circulationRepository;
    @Mock private HoldQueueRepository holdQueueRepository;
    @Mock private BackgroundJobRepository backgroundJobRepository;
    @Mock private AcquisitionSuggestionRepository acquisitionSuggestionRepository;

    @InjectMocks
    private AnalyticsServiceImpl analyticsService;

    @Test
    void getOverviewStatistics_shouldCountCopiesLoansOverdueHoldsAccurately() {
        when(documentRepository.count()).thenReturn(120L);
        when(physicalCopyRepository.count()).thenReturn(300L);
        when(physicalCopyRepository.countGroupedByStatus()).thenReturn(List.of(
                new Object[]{"AVAILABLE", 210L},
                new Object[]{"BORROWED", 65L},
                new Object[]{"RESERVED", 10L},
                new Object[]{"DAMAGED", 8L},
                new Object[]{"MAINTENANCE", 5L},
                new Object[]{"LOST", 2L}
        ));
        when(digitalCopyRepository.count()).thenReturn(45L);
        when(userRepository.count()).thenReturn(500L);
        when(userRepository.countByStatus("ACTIVE")).thenReturn(470L);
        when(circulationRepository.countByReturnDateIsNull()).thenReturn(65L);
        when(circulationRepository.countOverdueLoans(any())).thenReturn(9L, 2L);
        when(holdQueueRepository.countByStatusIn(any())).thenReturn(14L);
        when(backgroundJobRepository.countByStatus("FAILED")).thenReturn(3L);
        when(acquisitionSuggestionRepository.countByStatus("PENDING")).thenReturn(6L);
        when(circulationRepository.findPeriodSummary(any(), any(), isNull(), isNull(), any()))
                .thenReturn(Collections.singletonList(new Object[]{80L, 62L, 7L}));
        when(circulationRepository.findCirculationTrendByDay(any(), any(), isNull(), isNull(), any()))
                .thenReturn(List.of());
        when(circulationRepository.findBorrowsByDepartment(any(), any(), isNull(), isNull()))
                .thenReturn(List.of(new Object[]{"1", "Cong nghe thong tin", 60L}, new Object[]{"2", "DTVT", 20L}));
        when(circulationRepository.findBorrowsByDocType(any(), any(), isNull(), isNull()))
                .thenReturn(List.of(new Object[]{"textbook", 50L}, new Object[]{"book", 30L}));
        when(circulationRepository.findLowCirculationDocuments(any(), isNull(), isNull(), anyInt()))
                .thenReturn(List.of());

        LibraryOverviewStatsDto stats = analyticsService.getOverviewStatistics("LAST_7_DAYS", null, null, null, null);

        assertEquals(120L, stats.getTotalDocuments());
        assertEquals(300L, stats.getTotalPhysicalCopies());
        assertEquals(210L, stats.getAvailablePhysicalCopies());
        assertEquals(65L, stats.getBorrowedPhysicalCopies());
        assertEquals(65L, stats.getActiveLoans());
        assertEquals(9L, stats.getOverdueLoans());
        assertEquals(2L, stats.getSevereOverdueLoans());
        assertEquals(14L, stats.getPendingHolds());
        assertEquals(470L, stats.getActiveUsers());
        assertEquals(3L, stats.getFailedDigitalJobs());
        assertEquals(80L, stats.getTotalBorrowsInPeriod());
        assertEquals(62L, stats.getTotalReturnsInPeriod());
        assertEquals(7L, stats.getOverdueInPeriod());
        assertNotNull(stats.getInventoryBreakdown());
        assertEquals(8L, stats.getInventoryBreakdown().getDamaged());
        assertEquals(7, stats.getCirculationTrend().size());
        assertEquals(75.0, stats.getBorrowsByDepartment().get(0).getPercentage());
        assertEquals("Giáo trình", stats.getBorrowsByDocType().get(0).getLabel());
    }

    @Test
    void getPopularDocuments_shouldRejectOutOfRangeLimitAndDateOrder() {
        AppException limitEx = assertThrows(AppException.class,
                () -> analyticsService.getPopularDocuments("LAST_30_DAYS", null, null, 100, null, null, "BORROWS"));
        assertEquals(ErrorCode.INVALID_REQUEST, limitEx.getErrorCode());

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AppException dateEx = assertThrows(AppException.class,
                () -> analyticsService.getPopularDocuments(null, now, now.minusDays(5), 10, null, null, "BORROWS"));
        assertEquals(ErrorCode.INVALID_REQUEST, dateEx.getErrorCode());
    }

    @Test
    void getPopularDocuments_shouldMapRankedDtosWithAuthorsAndCopies() {
        UUID docId = UUID.randomUUID();
        when(circulationRepository.findPopularDocuments(any(), any(), isNull(), isNull(), eq("BORROWS"), eq(10)))
                .thenReturn(Collections.singletonList(new Object[]{
                        docId, "Cau truc du lieu", "textbook", 2023, null, 25L, 12L, 4L, 4.75, 41L
                }));

        Document doc = Document.builder().id(docId).title("Cau truc du lieu").build();
        Author author = Author.builder().id(1L).name("Nguyen Duc Nghia").build();
        DocumentAuthor da = DocumentAuthor.builder()
                .id(new DocumentAuthorId(docId, 1L))
                .document(doc)
                .author(author)
                .authorOrder(1)
                .build();
        when(documentAuthorRepository.findByDocumentIdInWithAuthor(List.of(docId))).thenReturn(List.of(da));
        when(physicalCopyRepository.countCopiesByDocumentIds(List.of(docId)))
                .thenReturn(Collections.singletonList(new Object[]{docId, 8L, 3L}));

        List<PopularDocumentDto> list = analyticsService.getPopularDocuments(
                "LAST_30_DAYS", null, null, 10, null, null, "BORROWS");

        assertEquals(1, list.size());
        PopularDocumentDto top = list.get(0);
        assertEquals(1, top.getRank());
        assertEquals(docId, top.getDocumentId());
        assertEquals(List.of("Nguyen Duc Nghia"), top.getAuthors());
        assertEquals(25L, top.getBorrowCount());
        assertEquals(41L, top.getInteractionCount());
        assertEquals(8L, top.getTotalPhysicalCopies());
        assertEquals(3L, top.getAvailablePhysicalCopies());
        assertEquals(4.75, top.getAverageRating());
    }

    @Test
    void getAcquisitionSuggestions_shouldMapDepartmentNameWithoutLazyLeak() {
        Department dept = Department.builder().id(1L).code("CNTT").name("Cong nghe thong tin").build();
        AcquisitionSuggestion entity = AcquisitionSuggestion.builder()
                .id(12L)
                .department(dept)
                .topic("rust programming")
                .queryCount(19)
                .status("PENDING")
                .build();

        when(acquisitionSuggestionRepository.findFilteredWithDepartment(eq("PENDING"), eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 10), 1));

        PageResponse<AcquisitionSuggestionDto> res = analyticsService.getAcquisitionSuggestions(
                "pending", 1L, PaginationRequest.builder().page(0).size(10).build());

        assertEquals(1, res.getTotalElements());
        AcquisitionSuggestionDto dto = res.getItems().get(0);
        assertEquals(1L, dto.getDepartmentId());
        assertEquals("Cong nghe thong tin", dto.getDepartmentName());
        assertEquals("rust programming", dto.getTopic());
    }
}
