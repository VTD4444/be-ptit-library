package vn.edu.ptit.library.recommendation.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.edu.ptit.library.catalog.entity.Document;
import vn.edu.ptit.library.catalog.entity.DocumentAuthor;
import vn.edu.ptit.library.catalog.repository.DigitalCopyRepository;
import vn.edu.ptit.library.catalog.repository.DocumentAuthorRepository;
import vn.edu.ptit.library.catalog.repository.DocumentRepository;
import vn.edu.ptit.library.catalog.repository.PhysicalCopyRepository;
import vn.edu.ptit.library.circulation.repository.CirculationRepository;
import vn.edu.ptit.library.common.util.SecurityUtils;
import vn.edu.ptit.library.integration.python.PythonWorkerClient;
import vn.edu.ptit.library.integration.python.dto.EasePredictRequest;
import vn.edu.ptit.library.integration.python.dto.EasePredictResponse;
import vn.edu.ptit.library.integration.python.dto.EaseRebuildRequest;
import vn.edu.ptit.library.integration.python.dto.EaseRebuildResponse;
import vn.edu.ptit.library.recommendation.dto.RecommendationItemDto;
import vn.edu.ptit.library.recommendation.entity.RecommendationCache;
import vn.edu.ptit.library.recommendation.repository.InteractionLogRepository;
import vn.edu.ptit.library.recommendation.repository.RecommendationCacheRepository;
import vn.edu.ptit.library.recommendation.service.RecommendationService;
import vn.edu.ptit.library.user.entity.UserProfile;
import vn.edu.ptit.library.user.repository.UserProfileRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 50;
    private static final double POSITIVE_WEIGHT_THRESHOLD = 3.0;
    private static final String COVER_URL_KEY = "coverUrl";

    private final RecommendationCacheRepository recommendationCacheRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final CirculationRepository circulationRepository;
    private final UserProfileRepository userProfileRepository;
    private final DocumentRepository documentRepository;
    private final DocumentAuthorRepository documentAuthorRepository;
    private final PhysicalCopyRepository physicalCopyRepository;
    private final DigitalCopyRepository digitalCopyRepository;
    private final PythonWorkerClient pythonWorkerClient;

    @Value("${app.recommendation.ease-model-version:ease-v1}")
    private String easeModelVersion;

    @Value("${app.recommendation.ease-lambda:500.0}")
    private double easeLambda;

    @Value("${app.recommendation.ease-cache-top-k:20}")
    private int easeCacheTopK;

    @Value("${app.recommendation.ease-cache-ttl-hours:24}")
    private int easeCacheTtlHours;

    @Value("${app.recommendation.ease-only-available:false}")
    private boolean easeOnlyAvailable;

    @Override
    @Transactional(readOnly = true)
    public List<RecommendationItemDto> getRecommendationsForUser(UUID userId, int limit) {
        int safeLimit = Math.max(MIN_LIMIT, Math.min(limit > 0 ? limit : 10, MAX_LIMIT));

        boolean personalizationEnabled = userProfileRepository.findById(userId)
                .map(UserProfile::getPersonalizationEnabled)
                .orElse(true);
        if (!personalizationEnabled) {
            return List.of();
        }

        try {
            EasePredictResponse online = pythonWorkerClient.predictPersonalEase(
                    EasePredictRequest.builder()
                            .userId(userId)
                            .limit(safeLimit)
                            .onlyAvailable(easeOnlyAvailable)
                            .build());
            if (online != null && online.isModelReady()) {
                if (!online.isPersonalizationEnabled() || online.getItems() == null || online.getItems().isEmpty()) {
                    return List.of();
                }
                List<ScoredEntry> entries = online.getItems().stream()
                        .map(it -> new ScoredEntry(it.getDocumentId(), it.getScore(), it.getRank(),
                                it.getReason(), it.getRecModelVersion()))
                        .toList();
                return enrichEntries(entries);
            }
        } catch (RuntimeException ex) {
            log.warn("Fallback ve recommendations_cache cho user {} do Python Worker loi/chua san sang: {}",
                    userId, ex.getMessage());
        }

        return loadSnapshotFallback(userId, safeLimit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecommendationItemDto> getMyRecommendations(int limit) {
        UUID userId = SecurityUtils.getCurrentUserIdOrThrow();
        return getRecommendationsForUser(userId, limit);
    }

    @Override
    public EaseRebuildResponse rebuildPersonalizedModel() {
        EaseRebuildRequest request = EaseRebuildRequest.builder()
                .modelVersion(easeModelVersion)
                .regLambda(easeLambda)
                .cacheTopK(easeCacheTopK)
                .cacheTtlHours(easeCacheTtlHours)
                .onlyAvailable(easeOnlyAvailable)
                .build();
        return pythonWorkerClient.rebuildPersonalEase(request);
    }

    private List<RecommendationItemDto> loadSnapshotFallback(UUID userId, int limit) {
        List<RecommendationCache> cachedList = recommendationCacheRepository
                .findValidRecommendationsByUserId(userId, OffsetDateTime.now());
        if (cachedList.isEmpty()) {
            return List.of();
        }

        Set<UUID> excluded = new HashSet<>(interactionLogRepository
                .findPositiveDocumentIdsByUserId(userId, POSITIVE_WEIGHT_THRESHOLD));
        circulationRepository.findByUserIdAndReturnDateIsNull(userId).forEach(loan -> {
            if (loan.getPhysicalCopy() != null && loan.getPhysicalCopy().getDocument() != null) {
                excluded.add(loan.getPhysicalCopy().getDocument().getId());
            }
        });

        List<ScoredEntry> entries = new ArrayList<>();
        int rank = 1;
        for (RecommendationCache cached : cachedList) {
            UUID docId = cached.getDocument().getId();
            if (excluded.contains(docId)) {
                continue;
            }
            entries.add(new ScoredEntry(
                    docId,
                    cached.getScore(),
                    rank++,
                    cached.getReason(),
                    cached.getAlgorithm().getRecModelVersion()
            ));
            if (entries.size() >= limit) {
                break;
            }
        }
        return enrichEntries(entries);
    }

    private List<RecommendationItemDto> enrichEntries(List<ScoredEntry> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }

        List<UUID> docIds = entries.stream().map(ScoredEntry::documentId).distinct().toList();

        Map<UUID, Document> docsById = new HashMap<>();
        documentRepository.findAllById(docIds).forEach(doc -> docsById.put(doc.getId(), doc));

        Map<UUID, List<String>> authorsByDoc = new HashMap<>();
        for (DocumentAuthor da : documentAuthorRepository.findByDocumentIdInWithAuthor(docIds)) {
            if (da.getAuthor() != null && da.getAuthor().getName() != null) {
                authorsByDoc.computeIfAbsent(da.getId().getDocumentId(), k -> new ArrayList<>())
                        .add(da.getAuthor().getName());
            }
        }

        Map<UUID, long[]> copiesByDoc = new HashMap<>();
        for (Object[] row : physicalCopyRepository.countCopiesByDocumentIds(docIds)) {
            UUID docId = (UUID) row[0];
            long total = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            long available = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            copiesByDoc.put(docId, new long[]{total, available});
        }

        Set<UUID> docsWithDigital = new HashSet<>(
                digitalCopyRepository.findDocumentIdsWithCurrentDigitalCopy(docIds));

        List<RecommendationItemDto> results = new ArrayList<>(entries.size());
        for (ScoredEntry entry : entries) {
            Document doc = docsById.get(entry.documentId());
            if (doc == null) {
                continue;
            }
            long[] counts = copiesByDoc.getOrDefault(doc.getId(), new long[]{0L, 0L});
            results.add(RecommendationItemDto.builder()
                    .documentId(doc.getId())
                    .title(doc.getTitle())
                    .authors(authorsByDoc.getOrDefault(doc.getId(), List.of()))
                    .coverUrl(resolveCoverUrl(doc))
                    .abstractText(doc.getAbstractText())
                    .docType(doc.getDocType())
                    .publicationYear(doc.getPublicationYear())
                    .totalPhysicalCopies(counts[0])
                    .availablePhysicalCopies(counts[1])
                    .hasDigitalCopy(docsWithDigital.contains(doc.getId()))
                    .score(entry.score())
                    .rank(entry.rank())
                    .reason(entry.reason())
                    .recModelVersion(entry.modelVersion())
                    .build());
        }
        return results;
    }

    private String resolveCoverUrl(Document doc) {
        if (StringUtils.hasText(doc.getCoverUrl())) {
            return doc.getCoverUrl();
        }
        if (doc.getMetadata() == null) {
            return null;
        }
        Object cover = doc.getMetadata().get(COVER_URL_KEY);
        return cover != null ? cover.toString() : null;
    }

    private record ScoredEntry(
            UUID documentId,
            Double score,
            Integer rank,
            String reason,
            String modelVersion
    ) {
    }
}
