package vn.edu.ptit.library.circulation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.library.circulation.dto.*;
import vn.edu.ptit.library.circulation.service.CirculationConfigurationService;
import vn.edu.ptit.library.common.dto.ApiResponse;
import java.util.List;

@RestController
@RequestMapping("/api/admin/penalty-types")
@RequiredArgsConstructor
public class PenaltyTypeAdminController {
    private final CirculationConfigurationService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    public ApiResponse<List<PenaltyTypeResponse>> getPenaltyTypes() {
        return ApiResponse.success(service.getPenaltyTypes());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PenaltyTypeResponse> createPenaltyType(@Valid @RequestBody PenaltyTypeCreateRequest request) {
        return ApiResponse.success(service.createPenaltyType(request));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PenaltyTypeResponse> updatePenaltyType(@Valid @RequestBody PenaltyTypeUpdateRequest request) {
        return ApiResponse.success(service.updatePenaltyType(request));
    }
}
