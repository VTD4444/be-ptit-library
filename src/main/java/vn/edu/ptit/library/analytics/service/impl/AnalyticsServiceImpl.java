package vn.edu.ptit.library.analytics.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.edu.ptit.library.analytics.dto.AcquisitionSuggestionDto;
import vn.edu.ptit.library.analytics.dto.AnalyticsPeriod;
import vn.edu.ptit.library.analytics.dto.CategoryStatDto;
import vn.edu.ptit.library.analytics.dto.CirculationTrendPointDto;
import vn.edu.ptit.library.analytics.dto.InventoryBreakdownDto;
import vn.edu.ptit.library.analytics.dto.LibraryOverviewStatsDto;
import vn.edu.ptit.library.analytics.dto.LowCirculationDocumentDto;
import vn.edu.ptit.library.analytics.dto.PopularDocumentDto;
import vn.edu.ptit.library.analytics.entity.AcquisitionSuggestion;
import vn.edu.ptit.library.analytics.repository.AcquisitionSuggestionRepository;
import vn.edu.ptit.library.analytics.service.AnalyticsService;
import vn.edu.ptit.library.catalog.entity.DocumentAuthor;
import vn.edu.ptit.library.catalog.repository.DigitalCopyRepository;
import vn.edu.ptit.library.catalog.repository.DocumentAuthorRepository;
import vn.edu.ptit.library.catalog.repository.DocumentRepository;
import vn.edu.ptit.library.catalog.repository.PhysicalCopyRepository;
import vn.edu.ptit.library.circulation.repository.CirculationRepository;
import vn.edu.ptit.library.circulation.repository.HoldQueueRepository;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;
import vn.edu.ptit.library.common.enums.AcquisitionStatus;
import vn.edu.ptit.library.common.enums.HoldStatus;
import vn.edu.ptit.library.common.enums.JobStatus;
import vn.edu.ptit.library.common.enums.PhysicalCopyStatus;
import vn.edu.ptit.library.common.enums.UserStatus;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;
import vn.edu.ptit.library.indexing.repository.BackgroundJobRepository;
import vn.edu.ptit.library.user.entity.Department;
import vn.edu.ptit.library.user.repository.UserRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final int MAX_POPULAR_LIMIT = 50;
    private static final int LOW_CIRCULATION_LIMIT = 6;
    private static final Set<String> ACQUISITION_SORT_FIELDS = Set.of("id", "queryCount", "createdAt", "updatedAt", "status");
    private static final DateTimeFormatter DAY_KEY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DAY_LABEL_FMT = DateTimeFormatter.ofPattern("dd/MM");

    private static final Map<String, String> DOC_TYPE_LABELS = Map.ofEntries(
            Map.entry("textbook", "Giáo trình"),
            Map.entry("book", "Sách chuyên khảo"),
            Map.entry("slide", "Bài giảng"),
            Map.entry("thesis", "Luận văn, luận án"),
            Map.entry("reference", "Sách tham khảo"),
            Map.entry("journal", "Tạp chí"),
            Map.entry("other", "Khác")
    );

    private final DocumentRepository documentRepository;
    private final PhysicalCopyRepository physicalCopyRepository;
    private final DigitalCopyRepository digitalCopyRepository;
    private final DocumentAuthorRepository documentAuthorRepository;
    private final UserRepository userRepository;
    private final CirculationRepository circulationRepository;
    private final HoldQueueRepository holdQueueRepository;
    private final BackgroundJobRepository backgroundJobRepository;
    private final AcquisitionSuggestionRepository acquisitionSuggestionRepository;

    @Override
    @Transactional(readOnly = true)
    public LibraryOverviewStatsDto getOverviewStatistics(
            String period,
            OffsetDateTime fromDate,
            OffsetDateTime toDate,
            Long departmentId,
            String docType) {

        AnalyticsPeriod.PeriodWindow window = AnalyticsPeriod.resolve(period, fromDate, toDate);
        String normalizedDocType = normalizeOptionalText(docType);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        long totalDocs = documentRepository.count();
        InventoryBreakdownDto breakdown = buildInventoryBreakdown();
        long totalCopies = physicalCopyRepository.count();
        long availCopies = breakdown.getAvailable();
        long borrowedCopies = breakdown.getBorrowed();
        long totalDigital = digitalCopyRepository.count();

        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByStatus(UserStatus.ACTIVE.name());

        long activeLoans = circulationRepository.countByReturnDateIsNull();
        long overdueLoans = circulationRepository.countOverdueLoans(now);
        long severeOverdueLoans = circulationRepository.countOverdueLoans(now.minusDays(30));

        long pendingHolds = holdQueueRepository.countByStatusIn(
                List.of(HoldStatus.WAITING.name(), HoldStatus.READY_FOR_PICKUP.name()));
        long failedDigitalJobs = backgroundJobRepository.countByStatus(JobStatus.FAILED.name());
        long pendingSuggestions = acquisitionSuggestionRepository.countByStatus(AcquisitionStatus.PENDING.name());

        long totalBorrowsInPeriod = 0L;
        long totalReturnsInPeriod = 0L;
        long overdueInPeriod = 0L;
        List<Object[]> periodRows = circulationRepository.findPeriodSummary(
                window.start(), window.end(), departmentId, normalizedDocType, now);
        if (periodRows != null && !periodRows.isEmpty()) {
            Object[] row = periodRows.get(0);
            totalBorrowsInPeriod = toLong(row[0]);
            totalReturnsInPeriod = toLong(row[1]);
            overdueInPeriod = toLong(row[2]);
        }

        List<CirculationTrendPointDto> trend = buildCirculationTrend(window, departmentId, normalizedDocType, now);
        List<CategoryStatDto> borrowsByDepartment = buildCategoryStats(
                circulationRepository.findBorrowsByDepartment(window.start(), window.end(), departmentId, normalizedDocType),
                false);
        List<CategoryStatDto> borrowsByDocType = buildCategoryStats(
                circulationRepository.findBorrowsByDocType(window.start(), window.end(), departmentId, normalizedDocType),
                true);
        List<LowCirculationDocumentDto> lowCirculation = buildLowCirculation(
                now.minusMonths(12), departmentId, normalizedDocType);

        return LibraryOverviewStatsDto.builder()
                .totalDocuments(totalDocs)
                .totalPhysicalCopies(totalCopies)
                .availablePhysicalCopies(availCopies)
                .borrowedPhysicalCopies(borrowedCopies)
                .totalDigitalCopies(totalDigital)
                .totalUsers(totalUsers)
                .activeLoans(activeLoans)
                .pendingAcquisitionSuggestions(pendingSuggestions)
                .period(window.periodName())
                .periodStart(window.start())
                .periodEnd(window.end())
                .activeUsers(activeUsers)
                .overdueLoans(overdueLoans)
                .severeOverdueLoans(severeOverdueLoans)
                .pendingHolds(pendingHolds)
                .failedDigitalJobs(failedDigitalJobs)
                .totalBorrowsInPeriod(totalBorrowsInPeriod)
                .totalReturnsInPeriod(totalReturnsInPeriod)
                .overdueInPeriod(overdueInPeriod)
                .inventoryBreakdown(breakdown)
                .circulationTrend(trend)
                .borrowsByDepartment(borrowsByDepartment)
                .borrowsByDocType(borrowsByDocType)
                .lowCirculationDocuments(lowCirculation)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PopularDocumentDto> getPopularDocuments(
            String period,
            OffsetDateTime fromDate,
            OffsetDateTime toDate,
            int limit,
            Long departmentId,
            String docType,
            String sortBy) {

        if (limit < 1 || limit > MAX_POPULAR_LIMIT) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "limit phai nam trong khoang tu 1 den 50");
        }

        String normalizedSort = normalizePopularSort(sortBy);
        AnalyticsPeriod.PeriodWindow window = AnalyticsPeriod.resolve(period, fromDate, toDate);
        String normalizedDocType = normalizeOptionalText(docType);

        List<Object[]> rows = circulationRepository.findPopularDocuments(
                window.start(), window.end(), departmentId, normalizedDocType, normalizedSort, limit);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<UUID> docIds = rows.stream()
                .map(row -> toUuid(row[0]))
                .toList();

        Map<UUID, List<String>> authorsByDoc = new HashMap<>();
        for (DocumentAuthor da : documentAuthorRepository.findByDocumentIdInWithAuthor(docIds)) {
            authorsByDoc.computeIfAbsent(da.getId().getDocumentId(), k -> new ArrayList<>())
                    .add(da.getAuthor().getName());
        }

        Map<UUID, long[]> copiesByDoc = new HashMap<>();
        for (Object[] row : physicalCopyRepository.countCopiesByDocumentIds(docIds)) {
            UUID docId = (UUID) row[0];
            copiesByDoc.put(docId, new long[]{toLong(row[1]), toLong(row[2])});
        }

        List<PopularDocumentDto> results = new ArrayList<>(rows.size());
        int rank = 1;
        for (Object[] row : rows) {
            UUID docId = toUuid(row[0]);
            long[] copyCounts = copiesByDoc.getOrDefault(docId, new long[]{0L, 0L});
            Double avgRating = row[8] != null ? roundTwoDecimals(((Number) row[8]).doubleValue()) : null;

            results.add(PopularDocumentDto.builder()
                    .rank(rank++)
                    .documentId(docId)
                    .title(row[1] != null ? row[1].toString() : "")
                    .authors(authorsByDoc.getOrDefault(docId, List.of()))
                    .docType(row[2] != null ? row[2].toString() : null)
                    .publicationYear(row[3] != null ? ((Number) row[3]).intValue() : null)
                    .coverUrl(row[4] != null ? row[4].toString() : null)
                    .totalPhysicalCopies(copyCounts[0])
                    .availablePhysicalCopies(copyCounts[1])
                    .borrowCount(toLong(row[5]))
                    .favoriteCount(toLong(row[6]))
                    .reviewCount(toLong(row[7]))
                    .averageRating(avgRating)
                    .interactionCount(toLong(row[9]))
                    .build());
        }
        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AcquisitionSuggestionDto> getAcquisitionSuggestions(
            String status,
            Long departmentId,
            PaginationRequest request) {

        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        Pageable pageable = toSafePageable(request, ACQUISITION_SORT_FIELDS, "queryCount");
        Page<AcquisitionSuggestion> page = acquisitionSuggestionRepository.findFilteredWithDepartment(
                normalizedStatus, departmentId, pageable);

        return PageResponse.from(page, this::toAcquisitionDto);
    }

    private InventoryBreakdownDto buildInventoryBreakdown() {
        long available = 0L;
        long borrowed = 0L;
        long reserved = 0L;
        long maintenance = 0L;
        long damaged = 0L;
        long lost = 0L;

        List<Object[]> rows = physicalCopyRepository.countGroupedByStatus();
        if (rows != null) {
            for (Object[] row : rows) {
                String status = row[0] != null ? row[0].toString().trim().toUpperCase() : "";
                long count = toLong(row[1]);
                if (PhysicalCopyStatus.AVAILABLE.name().equals(status)) {
                    available = count;
                } else if (PhysicalCopyStatus.BORROWED.name().equals(status)) {
                    borrowed = count;
                } else if (PhysicalCopyStatus.RESERVED.name().equals(status)) {
                    reserved = count;
                } else if (PhysicalCopyStatus.MAINTENANCE.name().equals(status)) {
                    maintenance = count;
                } else if (PhysicalCopyStatus.DAMAGED.name().equals(status)) {
                    damaged = count;
                } else if (PhysicalCopyStatus.LOST.name().equals(status)) {
                    lost = count;
                }
            }
        }

        return InventoryBreakdownDto.builder()
                .available(available)
                .borrowed(borrowed)
                .reserved(reserved)
                .maintenance(maintenance)
                .damaged(damaged)
                .lost(lost)
                .build();
    }

    private List<CirculationTrendPointDto> buildCirculationTrend(
            AnalyticsPeriod.PeriodWindow window,
            Long departmentId,
            String docType,
            OffsetDateTime now) {

        List<Object[]> rawRows = window.groupByMonth()
                ? circulationRepository.findCirculationTrendByMonth(window.start(), window.end(), departmentId, docType, now)
                : circulationRepository.findCirculationTrendByDay(window.start(), window.end(), departmentId, docType, now);

        Map<String, long[]> bucketMap = new LinkedHashMap<>();
        if (rawRows != null) {
            for (Object[] row : rawRows) {
                if (row[0] != null) {
                    bucketMap.put(row[0].toString(), new long[]{toLong(row[1]), toLong(row[2]), toLong(row[3])});
                }
            }
        }

        if (!window.groupByMonth() && window.start() != null) {
            LocalDate startDate = window.start().toLocalDate();
            LocalDate endDate = window.end().toLocalDate();
            long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
            if (days >= 0 && days <= 31) {
                List<CirculationTrendPointDto> points = new ArrayList<>((int) days + 1);
                for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                    String key = d.format(DAY_KEY_FMT);
                    long[] counts = bucketMap.getOrDefault(key, new long[]{0L, 0L, 0L});
                    points.add(CirculationTrendPointDto.builder()
                            .date(key)
                            .label(d.format(DAY_LABEL_FMT))
                            .borrowCount(counts[0])
                            .returnCount(counts[1])
                            .overdueCount(counts[2])
                            .build());
                }
                return points;
            }
        }

        List<CirculationTrendPointDto> points = new ArrayList<>(bucketMap.size());
        for (Map.Entry<String, long[]> entry : bucketMap.entrySet()) {
            String key = entry.getKey();
            long[] counts = entry.getValue();
            points.add(CirculationTrendPointDto.builder()
                    .date(key)
                    .label(formatBucketLabel(key, window.groupByMonth()))
                    .borrowCount(counts[0])
                    .returnCount(counts[1])
                    .overdueCount(counts[2])
                    .build());
        }
        return points;
    }

    private List<CategoryStatDto> buildCategoryStats(List<Object[]> rows, boolean isDocType) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        long total = 0L;
        for (Object[] row : rows) {
            total += toLong(isDocType ? row[1] : row[2]);
        }

        List<CategoryStatDto> stats = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            String key = row[0] != null ? row[0].toString() : "other";
            String label = isDocType
                    ? DOC_TYPE_LABELS.getOrDefault(key.toLowerCase(), key)
                    : (row[1] != null ? row[1].toString() : key);
            long count = toLong(isDocType ? row[1] : row[2]);
            double pct = total > 0 ? Math.round((count * 1000.0) / total) / 10.0 : 0.0;
            stats.add(CategoryStatDto.builder()
                    .key(key)
                    .label(label)
                    .count(count)
                    .percentage(pct)
                    .build());
        }
        return stats;
    }

    private List<LowCirculationDocumentDto> buildLowCirculation(
            OffsetDateTime sinceDate,
            Long departmentId,
            String docType) {
        List<Object[]> rows = circulationRepository.findLowCirculationDocuments(
                sinceDate, departmentId, docType, LOW_CIRCULATION_LIMIT);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<LowCirculationDocumentDto> list = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            list.add(LowCirculationDocumentDto.builder()
                    .documentId(toUuid(row[0]))
                    .title(row[1] != null ? row[1].toString() : "")
                    .docType(row[2] != null ? row[2].toString() : null)
                    .borrowCount(toLong(row[3]))
                    .lastBorrowedAt(toOffsetDateTime(row[4]))
                    .build());
        }
        return list;
    }

    private AcquisitionSuggestionDto toAcquisitionDto(AcquisitionSuggestion entity) {
        Department dept = entity.getDepartment();
        return AcquisitionSuggestionDto.builder()
                .id(entity.getId())
                .departmentId(dept != null ? dept.getId() : null)
                .departmentName(dept != null ? dept.getName() : null)
                .topic(entity.getTopic())
                .sourceQueryCluster(entity.getSourceQueryCluster())
                .queryCount(entity.getQueryCount())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String normalizePopularSort(String sortBy) {
        if (!StringUtils.hasText(sortBy)) {
            return "BORROWS";
        }
        String upper = sortBy.trim().toUpperCase();
        if ("BORROWS".equals(upper) || "INTERACTIONS".equals(upper)) {
            return upper;
        }
        throw new AppException(ErrorCode.INVALID_REQUEST, "sortBy khong hop le. Ho tro: BORROWS, INTERACTIONS");
    }

    private String normalizeOptionalText(String value) {
        if (!StringUtils.hasText(value) || "all".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim();
    }

    private Pageable toSafePageable(PaginationRequest request, Set<String> allowedSortFields, String defaultSortField) {
        int page = request != null ? Math.max(request.getPage(), 0) : 0;
        int size = request != null ? Math.max(1, Math.min(request.getSize(), 100)) : 20;
        String requestedSort = request != null ? request.getSortBy() : null;
        String sortBy = (requestedSort != null && allowedSortFields.contains(requestedSort))
                ? requestedSort
                : defaultSortField;
        Sort sort = (request != null && "asc".equalsIgnoreCase(request.getSortDir()))
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        return PageRequest.of(page, size, sort);
    }

    private String formatBucketLabel(String bucketKey, boolean groupByMonth) {
        if (bucketKey == null) return "";
        if (groupByMonth && bucketKey.length() == 7) {
            return bucketKey.substring(5, 7) + "/" + bucketKey.substring(0, 4);
        }
        if (!groupByMonth && bucketKey.length() == 10) {
            return bucketKey.substring(8, 10) + "/" + bucketKey.substring(5, 7);
        }
        return bucketKey;
    }

    private long toLong(Object val) {
        return val instanceof Number n ? n.longValue() : 0L;
    }

    private UUID toUuid(Object val) {
        if (val instanceof UUID u) {
            return u;
        }
        return UUID.fromString(val.toString());
    }

    private OffsetDateTime toOffsetDateTime(Object val) {
        if (val == null) return null;
        if (val instanceof OffsetDateTime odt) return odt;
        if (val instanceof Instant inst) return inst.atOffset(ZoneOffset.UTC);
        if (val instanceof Timestamp ts) return ts.toInstant().atOffset(ZoneOffset.UTC);
        return OffsetDateTime.parse(val.toString());
    }

    private Double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
