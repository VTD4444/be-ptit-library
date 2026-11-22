package vn.edu.ptit.library.analytics.service;

import vn.edu.ptit.library.analytics.dto.AcquisitionSuggestionDto;
import vn.edu.ptit.library.analytics.dto.LibraryOverviewStatsDto;
import vn.edu.ptit.library.analytics.dto.PopularDocumentDto;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;

import java.time.OffsetDateTime;
import java.util.List;

public interface AnalyticsService {

    LibraryOverviewStatsDto getOverviewStatistics(
            String period,
            OffsetDateTime fromDate,
            OffsetDateTime toDate,
            Long departmentId,
            String docType
    );

    List<PopularDocumentDto> getPopularDocuments(
            String period,
            OffsetDateTime fromDate,
            OffsetDateTime toDate,
            int limit,
            Long departmentId,
            String docType,
            String sortBy
    );

    PageResponse<AcquisitionSuggestionDto> getAcquisitionSuggestions(
            String status,
            Long departmentId,
            PaginationRequest request
    );
}
