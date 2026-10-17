package vn.edu.ptit.library.recommendation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.recommendation.entity.RecommendationCache;
import vn.edu.ptit.library.recommendation.entity.RecommendationCacheId;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface RecommendationCacheRepository extends JpaRepository<RecommendationCache, RecommendationCacheId> {

    @Query("SELECT r FROM RecommendationCache r WHERE r.user.id = :userId AND (r.expiresAt IS NULL OR r.expiresAt > :now) ORDER BY r.rank ASC")
    List<RecommendationCache> findValidRecommendationsByUserId(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);

    void deleteByUserId(UUID userId);
}
