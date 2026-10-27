package vn.edu.ptit.library.circulation.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.library.circulation.dto.*;
import vn.edu.ptit.library.circulation.service.CirculationConfigurationService;
import vn.edu.ptit.library.common.dto.ApiResponse;
import java.util.List;

@RestController
@RequestMapping("/api/admin/policies")
@RequiredArgsConstructor
public class PolicyAdminController {
    private final CirculationConfigurationService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    public ApiResponse<List<PolicyResponse>> getPolicies() {
        return ApiResponse.success(service.getPolicies());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PolicyResponse> updatePolicy(@PathVariable("id") @Positive Long id,
                                                   @Valid @RequestBody PolicyUpdateRequest request) {
        return ApiResponse.success(service.updatePolicy(id, request));
    }
}
