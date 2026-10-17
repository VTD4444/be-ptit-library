package vn.edu.ptit.library.analytics.service;

import vn.edu.ptit.library.analytics.dto.LibraryOverviewStatsDto;
import vn.edu.ptit.library.analytics.entity.AcquisitionSuggestion;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;

public interface AnalyticsService {

    LibraryOverviewStatsDto getOverviewStatistics();

    PageResponse<AcquisitionSuggestion> getAcquisitionSuggestions(String status, PaginationRequest request);
}
