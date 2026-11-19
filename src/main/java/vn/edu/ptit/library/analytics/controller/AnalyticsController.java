package vn.edu.ptit.library.analytics.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.library.analytics.dto.AcquisitionSuggestionDto;
import vn.edu.ptit.library.analytics.dto.LibraryOverviewStatsDto;
import vn.edu.ptit.library.analytics.dto.PopularDocumentDto;
import vn.edu.ptit.library.analytics.service.AnalyticsService;
import vn.edu.ptit.library.common.dto.ApiResponse;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics & Reports", description = "Thong ke tong quan, bao cao kho sach, top tai lieu pho bien va de xuat bo sung tai lieu")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    @Operation(summary = "Thong ke tong quan tinh trang thu vien theo khoang thoi gian (Can bo / Admin)")
    public ApiResponse<LibraryOverviewStatsDto> getOverviewStatistics(
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromDate,
            @RequestParam(value = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toDate,
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            @RequestParam(value = "docType", required = false) String docType) {
        return ApiResponse.success(
                analyticsService.getOverviewStatistics(period, fromDate, toDate, departmentId, docType));
    }

    @GetMapping("/popular-documents")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    @Operation(summary = "Top tai lieu duoc muon va tuong tac nhieu nhat (Can bo / Admin)")
    public ApiResponse<List<PopularDocumentDto>> getPopularDocuments(
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromDate,
            @RequestParam(value = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toDate,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            @RequestParam(value = "docType", required = false) String docType,
            @RequestParam(value = "sortBy", defaultValue = "BORROWS") String sortBy) {
        return ApiResponse.success(
                analyticsService.getPopularDocuments(period, fromDate, toDate, limit, departmentId, docType, sortBy));
    }

    @GetMapping("/acquisitions")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    @Operation(summary = "Danh sach de xuat bo sung tai lieu tu cum truy van rong (Can bo / Admin)")
    public ApiResponse<PageResponse<AcquisitionSuggestionDto>> getAcquisitionSuggestions(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            @Valid @ModelAttribute PaginationRequest request) {
        return ApiResponse.success(analyticsService.getAcquisitionSuggestions(status, departmentId, request));
    }
}
