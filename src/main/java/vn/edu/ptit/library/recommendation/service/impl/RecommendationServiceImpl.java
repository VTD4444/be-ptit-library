package vn.edu.ptit.library.recommendation.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.library.common.util.SecurityUtils;
import vn.edu.ptit.library.recommendation.dto.RecommendationItemDto;
import vn.edu.ptit.library.recommendation.entity.RecommendationCache;
import vn.edu.ptit.library.recommendation.repository.RecommendationCacheRepository;
import vn.edu.ptit.library.recommendation.service.RecommendationService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    private final RecommendationCacheRepository recommendationCacheRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RecommendationItemDto> getRecommendationsForUser(UUID userId, int limit) {
        List<RecommendationCache> cachedList = recommendationCacheRepository
                .findValidRecommendationsByUserId(userId, OffsetDateTime.now());

        return cachedList.stream()
                .limit(limit > 0 ? limit : 10)
                .map(r -> RecommendationItemDto.builder()
                        .documentId(r.getDocument().getId())
                        .title(r.getDocument().getTitle())
                        .docType(r.getDocument().getDocType())
                        .score(r.getScore())
                        .rank(r.getRank())
                        .reason(r.getReason())
                        .recModelVersion(r.getAlgorithm().getRecModelVersion())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecommendationItemDto> getMyRecommendations(int limit) {
        UUID userId = SecurityUtils.getCurrentUserIdOrThrow();
        return getRecommendationsForUser(userId, limit);
    }
}
