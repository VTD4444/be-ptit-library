package vn.edu.ptit.library.catalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.library.catalog.dto.DocumentReviewsResponseDto;
import vn.edu.ptit.library.catalog.dto.FavoriteToggleResponseDto;
import vn.edu.ptit.library.catalog.dto.ReviewDto;
import vn.edu.ptit.library.catalog.dto.ReviewRequestDto;
import vn.edu.ptit.library.catalog.service.DocumentInteractionService;
import vn.edu.ptit.library.common.dto.ApiResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;

import java.util.UUID;

@RestController
@RequestMapping("/api/catalog/interactions")
@RequiredArgsConstructor
@Tag(name = "Document Interactions", description = "Tuong tac doc gia: Yeu thich va Danh gia tai lieu")
public class DocumentInteractionController {

    private final DocumentInteractionService interactionService;

    @PostMapping("/favorites/{documentId}")
    @Operation(summary = "Them hoac bo tai lieu khoi danh sach yeu thich")
    public ApiResponse<FavoriteToggleResponseDto> toggleFavorite(@PathVariable("documentId") UUID documentId) {
        FavoriteToggleResponseDto result = interactionService.toggleFavorite(documentId);
        String message = result.isFavorite()
                ? "Đã thêm tài liệu vào tủ sách yêu thích"
                : "Đã xóa tài liệu khỏi tủ sách yêu thích";
        return ApiResponse.success(message, result);
    }

    @GetMapping("/favorites/{documentId}/status")
    @Operation(summary = "Kiem tra trang thai yeu thich cua tai lieu voi nguoi dung hien tai")
    public ApiResponse<FavoriteToggleResponseDto> isFavorite(@PathVariable("documentId") UUID documentId) {
        return ApiResponse.success(interactionService.isFavorite(documentId));
    }

    @PostMapping("/reviews/{documentId}")
    @Operation(summary = "Gui danh gia va binh luan cho tai lieu")
    public ApiResponse<ReviewDto> reviewDocument(
            @PathVariable("documentId") UUID documentId,
            @Valid @RequestBody ReviewRequestDto request) {
        return ApiResponse.success("Gửi đánh giá thành công", interactionService.addOrUpdateReview(documentId, request));
    }

    @GetMapping("/reviews/{documentId}")
    @Operation(summary = "Lay danh sach danh gia cua tai lieu co phan trang kem diem trung binh")
    public ApiResponse<DocumentReviewsResponseDto> getDocumentReviews(
            @PathVariable("documentId") UUID documentId,
            @Valid @ModelAttribute PaginationRequest request) {
        return ApiResponse.success(interactionService.getDocumentReviews(documentId, request));
    }
}
