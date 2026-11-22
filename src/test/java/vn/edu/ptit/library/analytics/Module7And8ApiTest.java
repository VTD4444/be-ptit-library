package vn.edu.ptit.library.analytics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.ptit.library.analytics.controller.AnalyticsController;
import vn.edu.ptit.library.analytics.dto.AcquisitionSuggestionDto;
import vn.edu.ptit.library.analytics.dto.LibraryOverviewStatsDto;
import vn.edu.ptit.library.analytics.dto.PopularDocumentDto;
import vn.edu.ptit.library.analytics.service.AnalyticsService;
import vn.edu.ptit.library.catalog.controller.DocumentInteractionController;
import vn.edu.ptit.library.catalog.dto.DocumentReviewsResponseDto;
import vn.edu.ptit.library.catalog.dto.FavoriteDocumentDto;
import vn.edu.ptit.library.catalog.dto.FavoriteToggleResponseDto;
import vn.edu.ptit.library.catalog.dto.ReviewDto;
import vn.edu.ptit.library.catalog.service.DocumentInteractionService;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.security.JwtAuthenticationEntryPoint;
import vn.edu.ptit.library.security.JwtAuthenticationFilter;
import vn.edu.ptit.library.security.JwtTokenProvider;
import vn.edu.ptit.library.security.SecurityConfig;
import vn.edu.ptit.library.user.controller.UserController;
import vn.edu.ptit.library.user.service.UserService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({DocumentInteractionController.class, UserController.class, AnalyticsController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class Module7And8ApiTest {

    private static final UUID DOC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired MockMvc mvc;
    @MockBean DocumentInteractionService interactionService;
    @MockBean UserService userService;
    @MockBean AnalyticsService analyticsService;
    @MockBean JwtTokenProvider tokenProvider;

    @ParameterizedTest
    @CsvSource({
            "GET,/api/analytics/overview,LIBRARIAN,200",
            "GET,/api/analytics/overview,ADMIN,200",
            "GET,/api/analytics/overview,READER,403",
            "GET,/api/analytics/popular-documents,LIBRARIAN,200",
            "GET,/api/analytics/popular-documents,ADMIN,200",
            "GET,/api/analytics/popular-documents,READER,403",
            "GET,/api/analytics/acquisitions,LIBRARIAN,200",
            "GET,/api/analytics/acquisitions,ADMIN,200",
            "GET,/api/analytics/acquisitions,READER,403",
            "GET,/api/users/me/favorites,READER,200",
            "GET,/api/users/me/favorites,LIBRARIAN,200"
    })
    void roleMatrix(String method, String path, String role, int expectedStatus) throws Exception {
        when(analyticsService.getOverviewStatistics(any(), any(), any(), any(), any()))
                .thenReturn(LibraryOverviewStatsDto.builder().totalDocuments(10).build());
        when(analyticsService.getPopularDocuments(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn(List.of());
        when(analyticsService.getAcquisitionSuggestions(any(), any(), any()))
                .thenReturn(PageResponse.<AcquisitionSuggestionDto>builder().items(List.of()).build());
        when(interactionService.getMyFavorites(any()))
                .thenReturn(PageResponse.<FavoriteDocumentDto>builder().items(List.of()).build());

        mvc.perform(request(HttpMethod.valueOf(method), path).with(user("tester").roles(role)))
                .andExpect(status().is(expectedStatus));

        if (expectedStatus == 403) {
            verifyNoInteractions(analyticsService);
        }
    }

    @Test
    void anonymousAccessRules() throws Exception {
        when(interactionService.getDocumentReviews(eq(DOC_ID), any())).thenReturn(
                DocumentReviewsResponseDto.builder()
                        .items(List.of())
                        .averageRating(4.5)
                        .totalReviews(2)
                        .ratingDistribution(Map.of(5, 1L, 4, 1L, 3, 0L, 2, 0L, 1, 0L))
                        .build()
        );

        // Public GET reviews allowed
        mvc.perform(get("/api/catalog/interactions/reviews/" + DOC_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value(4.5))
                .andExpect(jsonPath("$.data.totalReviews").value(2));

        // Private endpoints require auth
        mvc.perform(get("/api/users/me/favorites")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/analytics/overview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/analytics/popular-documents")).andExpect(status().isUnauthorized());
    }

    @Test
    void favoriteToggleAndReviewDtosSerializeCleanly() throws Exception {
        when(interactionService.toggleFavorite(DOC_ID)).thenReturn(
                FavoriteToggleResponseDto.builder().documentId(DOC_ID).isFavorite(true).build());

        mvc.perform(post("/api/catalog/interactions/favorites/" + DOC_ID).with(user("reader").roles("READER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(DOC_ID.toString()))
                .andExpect(jsonPath("$.data.isFavorite").value(true));

        when(interactionService.addOrUpdateReview(eq(DOC_ID), any())).thenReturn(
                ReviewDto.builder()
                        .id(99L)
                        .documentId(DOC_ID)
                        .rating(5)
                        .comment("Rat huu ich")
                        .userFullName("Nguyen Van A")
                        .user(ReviewDto.ReviewAuthorDto.builder().fullName("Nguyen Van A").userCode("B21DCCN001").build())
                        .build());

        mvc.perform(post("/api/catalog/interactions/reviews/" + DOC_ID)
                        .with(user("reader").roles("READER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"Rat huu ich\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.userFullName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());

        // Validation: rating > 5 should fail with 400
        mvc.perform(post("/api/catalog/interactions/reviews/" + DOC_ID)
                        .with(user("reader").roles("READER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":6,\"comment\":\"Invalid\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void popularDocumentsEndpointReturnsRankedDto() throws Exception {
        when(analyticsService.getPopularDocuments(eq("LAST_7_DAYS"), any(), any(), eq(10), any(), any(), eq("BORROWS")))
                .thenReturn(List.of(PopularDocumentDto.builder()
                        .rank(1)
                        .documentId(DOC_ID)
                        .title("Nhap mon AI")
                        .borrowCount(32)
                        .interactionCount(45)
                        .averageRating(4.8)
                        .build()));

        mvc.perform(get("/api/analytics/popular-documents?period=LAST_7_DAYS&limit=10")
                        .with(user("lib").roles("LIBRARIAN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[0].borrowCount").value(32))
                .andExpect(jsonPath("$.data[0].averageRating").value(4.8));
    }
}
