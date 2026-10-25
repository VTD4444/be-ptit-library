package vn.edu.ptit.library.analytics.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.library.analytics.dto.LibraryOverviewStatsDto;
import vn.edu.ptit.library.analytics.entity.AcquisitionSuggestion;
import vn.edu.ptit.library.analytics.service.AnalyticsService;
import vn.edu.ptit.library.common.dto.ApiResponse;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics & Reports", description = "Thong ke tong quan, bao cao kho sach va de xuat bo sung tai lieu")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    @Operation(summary = "Thong ke tong quan tinh trang thu vien (Can bo / Admin)")
    public ApiResponse<LibraryOverviewStatsDto> getOverviewStatistics() {
        return ApiResponse.success(analyticsService.getOverviewStatistics());
    }

    @GetMapping("/acquisitions")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    @Operation(summary = "Danh sach de xuat bo sung tai lieu tu cum truy van rong (Can bo / Admin)")
    public ApiResponse<PageResponse<AcquisitionSuggestion>> getAcquisitionSuggestions(
            @RequestParam(value = "status", required = false) String status,
            @Valid @ModelAttribute PaginationRequest request) {
        return ApiResponse.success(analyticsService.getAcquisitionSuggestions(status, request));
    }
}
