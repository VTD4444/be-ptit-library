package vn.edu.ptit.library.catalog.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.edu.ptit.library.catalog.dto.DocumentReviewsResponseDto;
import vn.edu.ptit.library.catalog.dto.FavoriteDocumentDto;
import vn.edu.ptit.library.catalog.dto.FavoriteToggleResponseDto;
import vn.edu.ptit.library.catalog.dto.ReviewDto;
import vn.edu.ptit.library.catalog.dto.ReviewRequestDto;
import vn.edu.ptit.library.catalog.entity.Document;
import vn.edu.ptit.library.catalog.entity.DocumentAuthor;
import vn.edu.ptit.library.catalog.entity.Favorite;
import vn.edu.ptit.library.catalog.entity.Review;
import vn.edu.ptit.library.catalog.repository.DigitalCopyRepository;
import vn.edu.ptit.library.catalog.repository.DocumentAuthorRepository;
import vn.edu.ptit.library.catalog.repository.DocumentRepository;
import vn.edu.ptit.library.catalog.repository.FavoriteRepository;
import vn.edu.ptit.library.catalog.repository.PhysicalCopyRepository;
import vn.edu.ptit.library.catalog.repository.ReviewRepository;
import vn.edu.ptit.library.catalog.service.DocumentInteractionService;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;
import vn.edu.ptit.library.common.util.SecurityUtils;
import vn.edu.ptit.library.user.entity.User;
import vn.edu.ptit.library.user.repository.UserRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentInteractionServiceImpl implements DocumentInteractionService {

    private static final Set<String> FAVORITE_SORT_FIELDS = Set.of("createdAt", "id");
    private static final Set<String> REVIEW_SORT_FIELDS = Set.of("createdAt", "updatedAt", "rating", "id");

    private final FavoriteRepository favoriteRepository;
    private final ReviewRepository reviewRepository;
    private final DocumentRepository documentRepository;
    private final DocumentAuthorRepository documentAuthorRepository;
    private final PhysicalCopyRepository physicalCopyRepository;
    private final DigitalCopyRepository digitalCopyRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public FavoriteToggleResponseDto toggleFavorite(UUID documentId) {
        UUID userId = SecurityUtils.getCurrentUserIdOrThrow();
        if (!documentRepository.existsById(documentId)) {
            throw new AppException(ErrorCode.DOCUMENT_NOT_FOUND);
        }

        Optional<Favorite> existing = favoriteRepository.findByUserIdAndDocumentId(userId, documentId);
        boolean isFav;
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            isFav = false;
        } else {
            User user = userRepository.getReferenceById(userId);
            Document document = documentRepository.getReferenceById(documentId);
            Favorite favorite = Favorite.builder()
                    .user(user)
                    .document(document)
                    .build();
            favoriteRepository.save(favorite);
            isFav = true;
        }

        return FavoriteToggleResponseDto.builder()
                .documentId(documentId)
                .isFavorite(isFav)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public FavoriteToggleResponseDto isFavorite(UUID documentId) {
        boolean isFav = SecurityUtils.getCurrentUserDetails()
                .map(u -> favoriteRepository.existsByUserIdAndDocumentId(u.getId(), documentId))
                .orElse(false);

        return FavoriteToggleResponseDto.builder()
                .documentId(documentId)
                .isFavorite(isFav)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FavoriteDocumentDto> getMyFavorites(PaginationRequest request) {
        UUID userId = SecurityUtils.getCurrentUserIdOrThrow();
        Pageable pageable = toSafePageable(request, FAVORITE_SORT_FIELDS, "createdAt");
        Page<Favorite> page = favoriteRepository.findActiveFavoritesByUserId(userId, pageable);

        List<Favorite> favorites = page.getContent();
        if (favorites.isEmpty()) {
            return PageResponse.from(page, f -> null);
        }

        List<UUID> documentIds = favorites.stream()
                .map(f -> f.getDocument().getId())
                .distinct()
                .toList();

        Map<UUID, List<String>> authorsByDoc = new HashMap<>();
        for (DocumentAuthor da : documentAuthorRepository.findByDocumentIdInWithAuthor(documentIds)) {
            authorsByDoc.computeIfAbsent(da.getId().getDocumentId(), k -> new ArrayList<>())
                    .add(da.getAuthor().getName());
        }

        Map<UUID, long[]> copiesByDoc = new HashMap<>();
        for (Object[] row : physicalCopyRepository.countCopiesByDocumentIds(documentIds)) {
            UUID docId = (UUID) row[0];
            long total = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            long available = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            copiesByDoc.put(docId, new long[]{total, available});
        }

        Set<UUID> docsWithDigital = new HashSet<>(digitalCopyRepository.findDocumentIdsWithCurrentDigitalCopy(documentIds));

        Map<UUID, Double> avgRatingByDoc = new HashMap<>();
        for (Object[] row : reviewRepository.getAverageRatingsByDocumentIds(documentIds)) {
            UUID docId = (UUID) row[0];
            Double avg = row[1] != null ? roundTwoDecimals(((Number) row[1]).doubleValue()) : null;
            avgRatingByDoc.put(docId, avg);
        }

        return PageResponse.from(page, favorite -> {
            Document doc = favorite.getDocument();
            UUID docId = doc.getId();
            long[] counts = copiesByDoc.getOrDefault(docId, new long[]{0L, 0L});
            return FavoriteDocumentDto.builder()
                    .id(docId)
                    .documentId(docId)
                    .title(doc.getTitle())
                    .authors(authorsByDoc.getOrDefault(docId, List.of()))
                    .coverUrl(resolveCoverUrl(doc))
                    .abstractText(doc.getAbstractText())
                    .docType(doc.getDocType())
                    .publicationYear(doc.getPublicationYear())
                    .totalPhysicalCopies(counts[0])
                    .availablePhysicalCopies(counts[1])
                    .hasDigitalCopy(docsWithDigital.contains(docId))
                    .averageRating(avgRatingByDoc.get(docId))
                    .favoritedAt(favorite.getCreatedAt())
                    .build();
        });
    }

    @Override
    @Transactional
    public ReviewDto addOrUpdateReview(UUID documentId, ReviewRequestDto request) {
        UUID userId = SecurityUtils.getCurrentUserIdOrThrow();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));

        Review review = reviewRepository.findByUserIdAndDocumentId(userId, documentId)
                .orElseGet(() -> Review.builder()
                        .user(user)
                        .document(document)
                        .build());

        review.setRating(request.getRating());
        String trimmedComment = request.getComment() != null ? request.getComment().trim() : null;
        review.setComment(StringUtils.hasText(trimmedComment) ? trimmedComment : null);

        Review saved = reviewRepository.save(review);
        return toReviewDto(saved, user, documentId);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentReviewsResponseDto getDocumentReviews(UUID documentId, PaginationRequest request) {
        if (!documentRepository.existsById(documentId)) {
            throw new AppException(ErrorCode.DOCUMENT_NOT_FOUND);
        }

        Pageable pageable = toSafePageable(request, REVIEW_SORT_FIELDS, "createdAt");
        Page<Review> page = reviewRepository.findByDocumentIdWithUser(documentId, pageable);

        Double rawAverage = reviewRepository.getAverageRatingForDocument(documentId);
        Double averageRating = rawAverage != null ? roundTwoDecimals(rawAverage) : null;

        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) {
            distribution.put(star, 0L);
        }
        long totalReviews = 0L;
        for (Object[] row : reviewRepository.countRatingDistributionByDocumentId(documentId)) {
            if (row[0] != null && row[1] != null) {
                int star = ((Number) row[0]).intValue();
                long count = ((Number) row[1]).longValue();
                if (distribution.containsKey(star)) {
                    distribution.put(star, count);
                }
                totalReviews += count;
            }
        }

        List<ReviewDto> items = page.getContent().stream()
                .map(r -> toReviewDto(r, r.getUser(), documentId))
                .toList();

        return DocumentReviewsResponseDto.builder()
                .items(items)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .averageRating(averageRating)
                .totalReviews(totalReviews)
                .ratingDistribution(distribution)
                .build();
    }

    private ReviewDto toReviewDto(Review review, User user, UUID documentId) {
        String fullName = user != null ? user.getFullName() : null;
        ReviewDto.ReviewAuthorDto authorDto = user != null
                ? ReviewDto.ReviewAuthorDto.builder()
                        .id(user.getId())
                        .fullName(user.getFullName())
                        .userCode(user.getUserCode())
                        .build()
                : null;

        return ReviewDto.builder()
                .id(review.getId())
                .documentId(documentId)
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .userFullName(fullName)
                .user(authorDto)
                .build();
    }

    private Pageable toSafePageable(PaginationRequest request, Set<String> allowedSortFields, String defaultSortField) {
        int page = request != null ? Math.max(request.getPage(), 0) : 0;
        int size = request != null ? Math.max(1, Math.min(request.getSize(), 100)) : 20;
        String requestedSort = request != null ? request.getSortBy() : null;
        String sortBy = (requestedSort != null && allowedSortFields.contains(requestedSort))
                ? requestedSort
                : defaultSortField;
        Sort sort = (request != null && "asc".equalsIgnoreCase(request.getSortDir()))
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        return PageRequest.of(page, size, sort);
    }

    private String resolveCoverUrl(Document doc) {
        if (StringUtils.hasText(doc.getCoverUrl())) {
            return doc.getCoverUrl();
        }
        if (doc.getMetadata() != null && doc.getMetadata().get("coverUrl") instanceof String metaCover && StringUtils.hasText(metaCover)) {
            return metaCover;
        }
        return null;
    }

    private Double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
