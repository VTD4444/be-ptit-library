package vn.edu.ptit.library.catalog;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.ptit.library.catalog.dto.DocumentReviewsResponseDto;
import vn.edu.ptit.library.catalog.dto.FavoriteDocumentDto;
import vn.edu.ptit.library.catalog.dto.FavoriteToggleResponseDto;
import vn.edu.ptit.library.catalog.dto.ReviewDto;
import vn.edu.ptit.library.catalog.dto.ReviewRequestDto;
import vn.edu.ptit.library.catalog.entity.Author;
import vn.edu.ptit.library.catalog.entity.Document;
import vn.edu.ptit.library.catalog.entity.DocumentAuthor;
import vn.edu.ptit.library.catalog.entity.DocumentAuthorId;
import vn.edu.ptit.library.catalog.entity.Favorite;
import vn.edu.ptit.library.catalog.entity.Review;
import vn.edu.ptit.library.catalog.repository.DigitalCopyRepository;
import vn.edu.ptit.library.catalog.repository.DocumentAuthorRepository;
import vn.edu.ptit.library.catalog.repository.DocumentRepository;
import vn.edu.ptit.library.catalog.repository.FavoriteRepository;
import vn.edu.ptit.library.catalog.repository.PhysicalCopyRepository;
import vn.edu.ptit.library.catalog.repository.ReviewRepository;
import vn.edu.ptit.library.catalog.service.impl.DocumentInteractionServiceImpl;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;
import vn.edu.ptit.library.security.CustomUserDetails;
import vn.edu.ptit.library.user.entity.User;
import vn.edu.ptit.library.user.repository.UserRepository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentInteractionServiceTest {

    @Mock private FavoriteRepository favoriteRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentAuthorRepository documentAuthorRepository;
    @Mock private PhysicalCopyRepository physicalCopyRepository;
    @Mock private DigitalCopyRepository digitalCopyRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DocumentInteractionServiceImpl service;

    private User reader;
    private Document document;

    @BeforeEach
    void setUp() {
        reader = User.builder()
                .id(UUID.randomUUID())
                .userCode("B21DCCN001")
                .email("reader@stu.ptit.edu.vn")
                .fullName("Nguyen Van A")
                .role("READER")
                .status("ACTIVE")
                .build();

        document = Document.builder()
                .id(UUID.randomUUID())
                .title("Nhap mon Tri tue nhan tao")
                .docType("textbook")
                .publicationYear(2024)
                .coverUrl("https://example.com/cover.jpg")
                .build();

        CustomUserDetails details = CustomUserDetails.builder()
                .id(reader.getId())
                .userCode(reader.getUserCode())
                .email(reader.getEmail())
                .password("hashed")
                .role(reader.getRole())
                .status(reader.getStatus())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void toggleFavorite_shouldThrowNotFoundWhenDocumentMissing() {
        UUID missingId = UUID.randomUUID();
        when(documentRepository.existsById(missingId)).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> service.toggleFavorite(missingId));
        assertEquals(ErrorCode.DOCUMENT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void toggleFavorite_shouldAddThenRemove() {
        when(documentRepository.existsById(document.getId())).thenReturn(true);
        when(favoriteRepository.findByUserIdAndDocumentId(reader.getId(), document.getId()))
                .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(reader.getId())).thenReturn(reader);
        when(documentRepository.getReferenceById(document.getId())).thenReturn(document);

        FavoriteToggleResponseDto added = service.toggleFavorite(document.getId());
        assertTrue(added.isFavorite());
        assertEquals(document.getId(), added.getDocumentId());
        verify(favoriteRepository).save(any(Favorite.class));

        Favorite existing = Favorite.builder().id(10L).user(reader).document(document).build();
        when(favoriteRepository.findByUserIdAndDocumentId(reader.getId(), document.getId()))
                .thenReturn(Optional.of(existing));

        FavoriteToggleResponseDto removed = service.toggleFavorite(document.getId());
        assertFalse(removed.isFavorite());
        verify(favoriteRepository).delete(existing);
    }

    @Test
    void getMyFavorites_shouldReturnPaginatedDtosWithBatchMetadata() {
        Favorite fav = Favorite.builder().id(1L).user(reader).document(document).build();
        when(favoriteRepository.findActiveFavoritesByUserId(eq(reader.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(fav), PageRequest.of(0, 10), 1));

        Author author = Author.builder().id(5L).name("Nguyen Thanh Thuy").build();
        DocumentAuthor da = DocumentAuthor.builder()
                .id(new DocumentAuthorId(document.getId(), 5L))
                .document(document)
                .author(author)
                .authorOrder(1)
                .build();
        when(documentAuthorRepository.findByDocumentIdInWithAuthor(List.of(document.getId())))
                .thenReturn(List.of(da));
        when(physicalCopyRepository.countCopiesByDocumentIds(List.of(document.getId())))
                .thenReturn(Collections.singletonList(new Object[]{document.getId(), 5L, 3L}));
        when(digitalCopyRepository.findDocumentIdsWithCurrentDigitalCopy(List.of(document.getId())))
                .thenReturn(List.of(document.getId()));
        when(reviewRepository.getAverageRatingsByDocumentIds(List.of(document.getId())))
                .thenReturn(Collections.singletonList(new Object[]{document.getId(), 4.6666}));

        PageResponse<FavoriteDocumentDto> page = service.getMyFavorites(PaginationRequest.builder().page(0).size(10).build());

        assertEquals(1, page.getTotalElements());
        FavoriteDocumentDto item = page.getItems().get(0);
        assertEquals(document.getId(), item.getDocumentId());
        assertEquals(List.of("Nguyen Thanh Thuy"), item.getAuthors());
        assertEquals(5L, item.getTotalPhysicalCopies());
        assertEquals(3L, item.getAvailablePhysicalCopies());
        assertTrue(item.isHasDigitalCopy());
        assertEquals(4.67, item.getAverageRating());
    }

    @Test
    void addOrUpdateReview_shouldReturnCleanReviewDto() {
        when(userRepository.findById(reader.getId())).thenReturn(Optional.of(reader));
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(reviewRepository.findByUserIdAndDocumentId(reader.getId(), document.getId())).thenReturn(Optional.empty());
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(55L);
            return r;
        });

        ReviewDto dto = service.addOrUpdateReview(document.getId(), ReviewRequestDto.builder()
                .rating(5)
                .comment("  Sach rat hay va de hieu  ")
                .build());

        assertEquals(55L, dto.getId());
        assertEquals(5, dto.getRating());
        assertEquals("Sach rat hay va de hieu", dto.getComment());
        assertEquals("Nguyen Van A", dto.getUserFullName());
        assertNotNull(dto.getUser());
        assertEquals("B21DCCN001", dto.getUser().getUserCode());
    }

    @Test
    void getDocumentReviews_shouldReturnAverageAndDistribution() {
        Review r1 = Review.builder().id(1L).user(reader).document(document).rating(5).comment("Tot").build();
        Review r2 = Review.builder().id(2L).user(reader).document(document).rating(4).comment("Kha").build();

        when(documentRepository.existsById(document.getId())).thenReturn(true);
        when(reviewRepository.findByDocumentIdWithUser(eq(document.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(r1, r2), PageRequest.of(0, 10), 2));
        when(reviewRepository.getAverageRatingForDocument(document.getId())).thenReturn(4.5);
        when(reviewRepository.countRatingDistributionByDocumentId(document.getId()))
                .thenReturn(List.of(new Object[]{5, 1L}, new Object[]{4, 1L}));

        DocumentReviewsResponseDto res = service.getDocumentReviews(
                document.getId(), PaginationRequest.builder().page(0).size(10).build());

        assertEquals(2, res.getItems().size());
        assertEquals(4.5, res.getAverageRating());
        assertEquals(2L, res.getTotalReviews());
        assertEquals(1L, res.getRatingDistribution().get(5));
        assertEquals(1L, res.getRatingDistribution().get(4));
        assertEquals(0L, res.getRatingDistribution().get(1));
    }
}
