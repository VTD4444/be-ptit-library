package vn.edu.ptit.library.indexing.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.library.common.dto.ApiResponse;
import vn.edu.ptit.library.indexing.entity.BackgroundJob;
import vn.edu.ptit.library.indexing.service.IndexingJobService;

@RestController
@RequestMapping("/api/indexing/jobs")
@RequiredArgsConstructor
@Tag(name = "Indexing Jobs", description = "Quan ly va theo doi tien trinh xu ly nen / boc tach PDF")
public class IndexingJobController {

    private final IndexingJobService indexingJobService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    @Operation(summary = "Xem trang thai tien trinh boc tach / danh chi muc PDF theo ID")
    public ApiResponse<BackgroundJob> getJobStatus(@PathVariable("id") Long id) {
        return ApiResponse.success(indexingJobService.getJobStatus(id));
    }
}
