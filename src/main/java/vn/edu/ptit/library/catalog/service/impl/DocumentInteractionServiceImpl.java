package vn.edu.ptit.library.catalog.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.library.catalog.dto.ReviewRequestDto;
import vn.edu.ptit.library.catalog.entity.Document;
import vn.edu.ptit.library.catalog.entity.Favorite;
import vn.edu.ptit.library.catalog.entity.Review;
import vn.edu.ptit.library.catalog.repository.DocumentRepository;
import vn.edu.ptit.library.catalog.repository.FavoriteRepository;
import vn.edu.ptit.library.catalog.repository.ReviewRepository;
import vn.edu.ptit.library.catalog.service.DocumentInteractionService;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;
import vn.edu.ptit.library.common.util.SecurityUtils;
import vn.edu.ptit.library.user.entity.User;
import vn.edu.ptit.library.user.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentInteractionServiceImpl implements DocumentInteractionService {

    private final FavoriteRepository favoriteRepository;
    private final ReviewRepository reviewRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public boolean toggleFavorite(UUID documentId) {
        UUID userId = SecurityUtils.getCurrentUserIdOrThrow();
        Optional<Favorite> existing = favoriteRepository.findByUserIdAndDocumentId(userId, documentId);

        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            return false;
        } else {
            User user = userRepository.getReferenceById(userId);
            Document document = documentRepository.getReferenceById(documentId);
            Favorite favorite = Favorite.builder()
                    .user(user)
                    .document(document)
                    .build();
            favoriteRepository.save(favorite);
            return true;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFavorite(UUID documentId) {
        return SecurityUtils.getCurrentUserDetails()
                .map(u -> favoriteRepository.existsByUserIdAndDocumentId(u.getId(), documentId))
                .orElse(false);
    }

    @Override
    @Transactional
    public Review addOrUpdateReview(UUID documentId, ReviewRequestDto request) {
        UUID userId = SecurityUtils.getCurrentUserIdOrThrow();
        User user = userRepository.getReferenceById(userId);
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));

        Review review = reviewRepository.findByUserIdAndDocumentId(userId, documentId)
                .orElseGet(() -> Review.builder()
                        .user(user)
                        .document(document)
                        .build());

        review.setRating(request.getRating());
        review.setComment(request.getComment());
        return reviewRepository.save(review);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<Review> getDocumentReviews(UUID documentId, PaginationRequest request) {
        Page<Review> page = reviewRepository.findByDocumentId(documentId, request.toPageable());
        return PageResponse.from(page);
    }
}
