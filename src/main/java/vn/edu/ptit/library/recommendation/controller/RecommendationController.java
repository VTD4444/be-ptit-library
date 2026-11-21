package vn.edu.ptit.library.recommendation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.library.common.dto.ApiResponse;
import vn.edu.ptit.library.integration.python.dto.EaseRebuildResponse;
import vn.edu.ptit.library.integration.python.dto.SimilarRebuildResponse;
import vn.edu.ptit.library.recommendation.dto.RecommendationItemDto;
import vn.edu.ptit.library.recommendation.dto.SimilarDocumentDto;
import vn.edu.ptit.library.recommendation.service.RecommendationService;
import vn.edu.ptit.library.recommendation.service.SimilarBookService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@Tag(name = "Recommendations", description = "Goi y tai lieu ca nhan hoa va sach tuong tu")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final SimilarBookService similarBookService;

    @GetMapping("/me")
    @Operation(summary = "Lay danh sach goi y tai lieu ca nhan hoa cho ban doc hien tai")
    public ApiResponse<List<RecommendationItemDto>> getMyRecommendations(
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return ApiResponse.success("Lay danh sach goi y thanh cong", recommendationService.getMyRecommendations(limit));
    }

    @PostMapping("/me/rebuild")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    @Operation(summary = "Kich hoat huan luyen lai ma tran EASE goi y ca nhan hoa tren Python Worker")
    public ApiResponse<EaseRebuildResponse> rebuildPersonalizedEase() {
        return ApiResponse.success(
                "Huan luyen lai mo hinh goi y ca nhan hoa thanh cong",
                recommendationService.rebuildPersonalizedModel());
    }

    @GetMapping("/similar/{documentId}")
    @Operation(summary = "Lay danh sach tai lieu tuong tu theo noi dung cho trang chi tiet tai lieu")
    public ApiResponse<List<SimilarDocumentDto>> getSimilarBooks(
            @PathVariable("documentId") UUID documentId,
            @RequestParam(value = "limit", defaultValue = "6") int limit) {
        return ApiResponse.success(
                "Lay danh sach tai lieu tuong tu thanh cong",
                similarBookService.getSimilarBooks(documentId, limit));
    }

    @PostMapping("/similar/rebuild")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    @Operation(summary = "Kich hoat tinh lai ma tran tai lieu tuong tu tren Python Worker")
    public ApiResponse<SimilarRebuildResponse> rebuildSimilarBooks() {
        return ApiResponse.success(
                "Tinh lai danh sach tai lieu tuong tu thanh cong",
                similarBookService.rebuildSimilarBooks());
    }
}
