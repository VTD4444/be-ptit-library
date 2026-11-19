package vn.edu.ptit.library.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LibraryOverviewStatsDto {

    private long totalDocuments;
    private long totalPhysicalCopies;
    private long availablePhysicalCopies;
    private long borrowedPhysicalCopies;
    private long totalDigitalCopies;
    private long totalUsers;
    private long activeLoans;
    private long pendingAcquisitionSuggestions;

    // Extended analytics metrics
    private String period;
    private OffsetDateTime periodStart;
    private OffsetDateTime periodEnd;
    private long activeUsers;
    private long overdueLoans;
    private long severeOverdueLoans;
    private long pendingHolds;
    private long failedDigitalJobs;
    private long totalBorrowsInPeriod;
    private long totalReturnsInPeriod;
    private long overdueInPeriod;
    private InventoryBreakdownDto inventoryBreakdown;
    private List<CirculationTrendPointDto> circulationTrend;
    private List<CategoryStatDto> borrowsByDepartment;
    private List<CategoryStatDto> borrowsByDocType;
    private List<LowCirculationDocumentDto> lowCirculationDocuments;
}
