package vn.edu.ptit.library.search.controller;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.library.common.dto.ApiResponse;
import vn.edu.ptit.library.search.dto.SearchConfigResponse;
import vn.edu.ptit.library.search.service.SearchConfigurationService;
import java.util.List;

@RestController
@RequestMapping("/api/admin/search-configs")
@RequiredArgsConstructor
public class SearchConfigAdminController {
    private final SearchConfigurationService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<SearchConfigResponse>> getConfigurations() {
        return ApiResponse.success(service.getConfigurations());
    }

    @PutMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SearchConfigResponse> activate(@PathVariable("id") @Positive Long id) {
        return ApiResponse.success(service.activate(id));
    }
}
