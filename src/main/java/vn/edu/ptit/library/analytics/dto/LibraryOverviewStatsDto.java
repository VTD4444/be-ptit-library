package vn.edu.ptit.library.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
}
