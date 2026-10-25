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
import vn.edu.ptit.library.catalog.dto.ReviewRequestDto;
import vn.edu.ptit.library.catalog.entity.Review;
import vn.edu.ptit.library.catalog.service.DocumentInteractionService;
import vn.edu.ptit.library.common.dto.ApiResponse;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/catalog/interactions")
@RequiredArgsConstructor
@Tag(name = "Document Interactions", description = "Tuong tac doc gia: Yeu thich va Danh gia tai lieu")
public class DocumentInteractionController {

    private final DocumentInteractionService interactionService;

    @PostMapping("/favorites/{documentId}")
    @Operation(summary = "Them hoac bo tai lieu khoi danh sach yeu thich")
    public ApiResponse<Map<String, Object>> toggleFavorite(@PathVariable("documentId") UUID documentId) {
        boolean isFav = interactionService.toggleFavorite(documentId);
        return ApiResponse.success(Map.of("documentId", documentId, "isFavorite", isFav));
    }

    @GetMapping("/favorites/{documentId}/status")
    @Operation(summary = "Kiem tra trang thai yeu thich cua tai lieu voi nguoi dung hien tai")
    public ApiResponse<Map<String, Object>> isFavorite(@PathVariable("documentId") UUID documentId) {
        boolean isFav = interactionService.isFavorite(documentId);
        return ApiResponse.success(Map.of("documentId", documentId, "isFavorite", isFav));
    }

    @PostMapping("/reviews/{documentId}")
    @Operation(summary = "Gui danh gia va binh luan cho tai lieu")
    public ApiResponse<Review> reviewDocument(
            @PathVariable("documentId") UUID documentId,
            @Valid @RequestBody ReviewRequestDto request) {
        return ApiResponse.success("Gui danh gia thanh cong", interactionService.addOrUpdateReview(documentId, request));
    }

    @GetMapping("/reviews/{documentId}")
    @Operation(summary = "Lay danh sach danh gia cua tai lieu co phan trang")
    public ApiResponse<PageResponse<Review>> getDocumentReviews(
            @PathVariable("documentId") UUID documentId,
            @Valid @ModelAttribute PaginationRequest request) {
        return ApiResponse.success(interactionService.getDocumentReviews(documentId, request));
    }
}
