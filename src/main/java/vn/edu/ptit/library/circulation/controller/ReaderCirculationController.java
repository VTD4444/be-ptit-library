package vn.edu.ptit.library.circulation.controller;

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
import vn.edu.ptit.library.circulation.dto.CirculationHistoryItemDto;
import vn.edu.ptit.library.circulation.dto.UserFinesSummaryDto;
import vn.edu.ptit.library.circulation.service.CirculationService;
import vn.edu.ptit.library.common.dto.ApiResponse;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
@Tag(name = "Reader Circulation & Fines", description = "Tra cuu lich su muon/tra va cong no/tien phat ca nhan")
public class ReaderCirculationController {

    private final CirculationService circulationService;

    @GetMapping("/circulation-history")
    @PreAuthorize("hasAnyRole('READER', 'LIBRARIAN', 'ADMIN')")
    @Operation(
            summary = "Xem lich su muon/tra ca nhan (Reader)",
            description = "Ho tro phan trang va loc theo trang thai (BORROWING, RETURNED, OVERDUE)."
    )
    public ApiResponse<PageResponse<CirculationHistoryItemDto>> getMyCirculationHistory(
            @RequestParam(value = "status", required = false) String status,
            @Valid @ModelAttribute PaginationRequest request) {
        return ApiResponse.success(circulationService.getMyCirculationHistory(status, request));
    }

    @GetMapping("/fines")
    @PreAuthorize("hasAnyRole('READER', 'LIBRARIAN', 'ADMIN')")
    @Operation(
            summary = "Xem cong no, tien phat ca nhan (Reader)",
            description = "Tra ve tong cong no chua nop, tong da nop va danh sach chi tiet cac khoan phat (Ly do, So tien, Trang thai UNPAID/PAID)."
    )
    public ApiResponse<UserFinesSummaryDto> getMyFines(
            @RequestParam(value = "status", required = false) String status) {
        return ApiResponse.success(circulationService.getMyFines(status));
    }
}
