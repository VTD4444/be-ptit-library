package vn.edu.ptit.library.recommendation.service;

import vn.edu.ptit.library.recommendation.dto.RecommendationItemDto;

import java.util.List;
import java.util.UUID;

public interface RecommendationService {

    List<RecommendationItemDto> getRecommendationsForUser(UUID userId, int limit);

    List<RecommendationItemDto> getMyRecommendations(int limit);
}
