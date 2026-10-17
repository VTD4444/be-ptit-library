package vn.edu.ptit.library.catalog.service;

import vn.edu.ptit.library.catalog.dto.ReviewRequestDto;
import vn.edu.ptit.library.catalog.entity.Review;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;

import java.util.UUID;

public interface DocumentInteractionService {

    boolean toggleFavorite(UUID documentId);

    boolean isFavorite(UUID documentId);

    Review addOrUpdateReview(UUID documentId, ReviewRequestDto request);

    PageResponse<Review> getDocumentReviews(UUID documentId, PaginationRequest request);
}
