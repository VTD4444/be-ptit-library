package vn.edu.ptit.library.recommendation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.library.common.dto.ApiResponse;
import vn.edu.ptit.library.recommendation.dto.RecommendationItemDto;
import vn.edu.ptit.library.recommendation.service.RecommendationService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
@Tag(name = "Recommendations", description = "Goi y tai lieu ca nhan hoa dua tren lich su doc, nganh hoc va dac trung vector")
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/me")
    @Operation(summary = "Lay danh sach goi y tai lieu ca nhan hoa cho ban doc hien tai")
    public ApiResponse<List<RecommendationItemDto>> getMyRecommendations(
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return ApiResponse.success("Lay danh sach goi y thanh cong", recommendationService.getMyRecommendations(limit));
    }
}
