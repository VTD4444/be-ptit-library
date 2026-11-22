package vn.edu.ptit.library.catalog.service;

import vn.edu.ptit.library.catalog.dto.DocumentReviewsResponseDto;
import vn.edu.ptit.library.catalog.dto.FavoriteDocumentDto;
import vn.edu.ptit.library.catalog.dto.FavoriteToggleResponseDto;
import vn.edu.ptit.library.catalog.dto.ReviewDto;
import vn.edu.ptit.library.catalog.dto.ReviewRequestDto;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;

import java.util.UUID;

public interface DocumentInteractionService {

    FavoriteToggleResponseDto toggleFavorite(UUID documentId);

    FavoriteToggleResponseDto isFavorite(UUID documentId);

    PageResponse<FavoriteDocumentDto> getMyFavorites(PaginationRequest request);

    ReviewDto addOrUpdateReview(UUID documentId, ReviewRequestDto request);

    DocumentReviewsResponseDto getDocumentReviews(UUID documentId, PaginationRequest request);
}
