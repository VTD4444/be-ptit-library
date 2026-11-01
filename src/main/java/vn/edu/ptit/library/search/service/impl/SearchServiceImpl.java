package vn.edu.ptit.library.search.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.edu.ptit.library.catalog.entity.PhysicalCopy;
import vn.edu.ptit.library.catalog.repository.DocumentRepository;
import vn.edu.ptit.library.catalog.repository.PhysicalCopyRepository;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;
import vn.edu.ptit.library.common.util.SecurityUtils;
import vn.edu.ptit.library.indexing.service.IndexingJobService;
import vn.edu.ptit.library.integration.python.PythonWorkerClient;
import vn.edu.ptit.library.integration.python.dto.TfidfSearchRequest;
import vn.edu.ptit.library.integration.python.dto.TfidfSearchResponse;
import vn.edu.ptit.library.search.dto.ContentSearchRequest;
import vn.edu.ptit.library.search.dto.ContentSearchResultDto;
import vn.edu.ptit.library.search.dto.DocumentMatchesResponse;
import vn.edu.ptit.library.search.dto.SmartSearchRequest;
import vn.edu.ptit.library.search.dto.SmartSearchResponse;
import vn.edu.ptit.library.search.dto.TextLocationDto;
import vn.edu.ptit.library.search.entity.SearchVectorConfig;
import vn.edu.ptit.library.search.repository.ContentSearchRepository;
import vn.edu.ptit.library.search.repository.ContentSearchRepository.DocumentFilter;
import vn.edu.ptit.library.search.repository.ContentSearchRepository.DocumentSummary;
import vn.edu.ptit.library.search.repository.ContentSearchRepository.ScoredChunk;
import vn.edu.ptit.library.search.repository.SearchVectorConfigRepository;
import vn.edu.ptit.library.search.service.SearchService;
import vn.edu.ptit.library.search.util.SearchTextUtils;
import vn.edu.ptit.library.user.entity.User;
import vn.edu.ptit.library.user.repository.UserRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private static final String MODE_TFIDF = "TFIDF";
    private static final String MODE_KEYWORD_ONLY = "KEYWORD_ONLY";
    // Trich doan Python tra ve da duoc cat quanh vung khop; o day chi escape + boc <mark>
    private static final int SNIPPET_MAX_CHARS = 1000;
    private static final int FALLBACK_SNIPPET_CHARS = 300;
    private static final int MAX_MATCHES_PER_DOCUMENT = 50;
    private static final int MAX_LOCATIONS_PER_RESULT = 5;
    private static final int MAX_LOCATIONS_PER_MATCH = 50;

    private final SearchVectorConfigRepository configRepository;
    private final PhysicalCopyRepository physicalCopyRepository;
    private final PythonWorkerClient pythonWorkerClient;
    private final IndexingJobService indexingJobService;
    private final UserRepository userRepository;
    private final ContentSearchRepository contentSearchRepository;
    private final DocumentRepository documentRepository;

    // Nguong cosine TF-IDF toi thieu de coi la khop
    @Value("${app.search.min-score:0.05}")
    private double minScore;

    /**
     * Mot doan van ban khop: tu Python Worker (cac chunk goi nhau da gop, co vi tri chinh xac)
     * hoac tu Full-Text Search du phong (mot chunk, chi biet trang).
     */
    private record Passage(UUID documentId, DocumentSummary document, UUID digitalCopyId, List<UUID> chunkIds,
                           double score, Integer pageStart, Integer pageEnd, String chapter, String section,
                           Integer wordStart, Integer wordEnd, String snippet, List<String> highlightTerms,
                           List<TextLocationDto> locations) {
    }

    private record Ranking(List<Passage> passages, String mode, boolean booleanQuery) {
    }

    @Override
    public List<ContentSearchResultDto> searchContent(ContentSearchRequest request) {
        String query = SearchTextUtils.normalize(request.getQuery());
        List<Passage> results = rank(query, SearchTextUtils.extractTerms(query), null, null, false,
                request.getLimit(), MAX_LOCATIONS_PER_RESULT).passages();

        // Tinh maxScore de ghi nhan lich su va phat hien truy van khong ket qua (Muc 2.4 & 2.5)
        logSearch(request.getQuery(), results.isEmpty() ? 0.0 : results.get(0).score(), results.size());

        return results.stream().map(r -> {
            List<PhysicalCopy> copies = physicalCopyRepository.findAvailableCopiesByDocumentId(r.documentId());
            String shelfLocation = copies.isEmpty() ? null : copies.get(0).getShelfLocation();

            return ContentSearchResultDto.builder()
                    .documentId(r.documentId())
                    .documentTitle(r.document().title())
                    .chunkId(r.chunkIds().isEmpty() ? null : r.chunkIds().get(0))
                    .chunkText(r.snippet())
                    .pageNumber(r.pageStart())
                    .chapter(r.chapter())
                    .section(r.section())
                    .relevanceScore(r.score())
                    .availablePhysicalCopies(copies.size())
                    .shelfLocation(shelfLocation)
                    .build();
        }).toList();
    }

    @Override
    public SmartSearchResponse smartSearch(SmartSearchRequest request) {
        String query = SearchTextUtils.normalize(request.getQuery());
        if (query.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Câu truy vấn không được để trống");
        }
        List<String> terms = SearchTextUtils.extractTerms(query);
        DocumentFilter filter = new DocumentFilter(
                blankToNull(request.getLanguage()), blankToNull(request.getDocType()), request.getDepartmentId());

        Ranking ranking = rank(query, terms, null, filter, true, request.getLimit(), MAX_LOCATIONS_PER_RESULT);
        List<Passage> hits = ranking.passages();
        logSearch(query, hits.isEmpty() ? 0.0 : hits.get(0).score(), hits.size());

        return SmartSearchResponse.builder()
                .query(query)
                .searchMode(ranking.mode())
                .booleanQuery(ranking.booleanQuery())
                .highlightTerms(terms)
                .results(hits.stream().map(hit -> SmartSearchResponse.Item.builder()
                        .documentId(hit.documentId())
                        .title(hit.document().title())
                        .docType(hit.document().docType())
                        .language(hit.document().language())
                        .publicationYear(hit.document().publicationYear())
                        .matchScore(toPercent(hit.score()))
                        .snippet(snippetHtml(hit, terms))
                        .digitalCopyId(hit.digitalCopyId())
                        .chunkIds(hit.chunkIds())
                        .pageNumber(hit.pageStart())
                        .pageEnd(hit.pageEnd())
                        .chapter(hit.chapter())
                        .section(hit.section())
                        .locations(hit.locations())
                        .build()).toList())
                .build();
    }

    @Override
    public DocumentMatchesResponse findMatches(UUID documentId, String rawQuery, int limit) {
        String query = SearchTextUtils.normalize(rawQuery);
        if (query.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Nội dung tìm kiếm không được để trống");
        }
        if (!documentRepository.existsById(documentId)) {
            throw new AppException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        List<String> terms = SearchTextUtils.extractTerms(query);
        Ranking ranking = rank(query, terms, documentId, null, false,
                Math.max(1, Math.min(limit, MAX_MATCHES_PER_DOCUMENT)), MAX_LOCATIONS_PER_MATCH);

        return DocumentMatchesResponse.builder()
                .documentId(documentId)
                .query(query)
                .searchMode(ranking.mode())
                .booleanQuery(ranking.booleanQuery())
                .highlightTerms(terms)
                .matches(ranking.passages().stream().map(hit -> DocumentMatchesResponse.Match.builder()
                        .chunkIds(hit.chunkIds())
                        .matchScore(toPercent(hit.score()))
                        .pageNumber(hit.pageStart())
                        .pageEnd(hit.pageEnd())
                        .chapter(hit.chapter())
                        .section(hit.section())
                        .wordStart(hit.wordStart())
                        .wordEnd(hit.wordEnd())
                        .snippet(snippetHtml(hit, terms))
                        .locations(hit.locations())
                        .build()).toList())
                .build();
    }

    /**
     * Tim tren Python Worker (Boolean + TF-IDF + gop chunk + dinh vi); Worker loi thi du phong Full-Text Search.
     */
    private Ranking rank(String query, List<String> terms, UUID documentId, DocumentFilter filter,
                         boolean bestPerDocument, int limit, int maxLocations) {
        Collection<UUID> allowedDocuments = null;
        if (documentId != null) {
            allowedDocuments = List.of(documentId);
        } else if (filter != null && !filter.isEmpty()) {
            allowedDocuments = contentSearchRepository.findDocumentIds(filter);
            if (allowedDocuments.isEmpty()) {
                return new Ranking(List.of(), MODE_TFIDF, false);
            }
        }

        try {
            TfidfSearchResponse response = pythonWorkerClient.searchTfidf(TfidfSearchRequest.builder()
                    .query(query)
                    .limit(limit)
                    .bestPerDocument(bestPerDocument)
                    .documentIds(allowedDocuments)
                    .minScore(minScore)
                    .maxLocations(maxLocations)
                    .build());
            List<TfidfSearchResponse.Passage> passages = response != null && response.getPassages() != null
                    ? response.getPassages() : List.of();
            return new Ranking(toPassages(passages), MODE_TFIDF, response != null && response.isBooleanQuery());
        } catch (Exception ex) {
            log.warn("Khong tim kiem duoc tren Python Worker, du phong Full-Text Search: {}", ex.getMessage());
        }

        String tsQuery = SearchTextUtils.toTsQuery(terms);
        if (tsQuery == null) {
            return new Ranking(List.of(), MODE_KEYWORD_ONLY, false);
        }
        Long configId = configRepository.findByIsActiveTrue().map(SearchVectorConfig::getId).orElse(null);
        List<ScoredChunk> chunks = contentSearchRepository.searchFullText(
                tsQuery, configId, documentId, filter, bestPerDocument, limit);
        return new Ranking(chunks.stream().map(SearchServiceImpl::fromChunk).toList(), MODE_KEYWORD_ONLY, false);
    }

    private List<Passage> toPassages(List<TfidfSearchResponse.Passage> passages) {
        // Chi giu doan cua tai lieu chua bi xoa va ban so van la ban hien hanh (mo hinh co the cu hon DB)
        Map<UUID, DocumentSummary> documents = contentSearchRepository.findDocumentSummaries(
                passages.stream().map(TfidfSearchResponse.Passage::getDocumentId).distinct().toList(),
                passages.stream().map(TfidfSearchResponse.Passage::getDigitalCopyId).distinct().toList());
        return passages.stream()
                .filter(p -> documents.containsKey(p.getDocumentId()))
                .map(p -> new Passage(p.getDocumentId(), documents.get(p.getDocumentId()), p.getDigitalCopyId(),
                        nullToEmpty(p.getChunkIds()), p.getScore(), p.getPageStart(), p.getPageEnd(),
                        p.getChapter(), p.getSection(), p.getWordStart(), p.getWordEnd(), p.getSnippet(),
                        nullToEmpty(p.getHighlightTerms()), nullToEmpty(p.getLocations())))
                .toList();
    }

    private static Passage fromChunk(ScoredChunk hit) {
        var c = hit.chunk();
        return new Passage(c.documentId(), new DocumentSummary(c.title(), c.docType(), c.language(), c.publicationYear()),
                null, List.of(c.chunkId()), hit.score(), c.pageNumber(), c.pageEnd(), c.chapter(), c.section(),
                null, null, c.chunkText(), List.of(), List.of());
    }

    private static String snippetHtml(Passage passage, List<String> queryTerms) {
        // Uu tien am tiet thuc su khop do Python dinh vi (da qua tach tu, dong nghia); du phong la tu cua truy van
        List<String> terms = passage.highlightTerms().isEmpty() ? queryTerms : passage.highlightTerms();
        int maxChars = passage.wordStart() != null ? SNIPPET_MAX_CHARS : FALLBACK_SNIPPET_CHARS;
        return SearchTextUtils.buildSnippet(passage.snippet(), terms, maxChars);
    }

    private void logSearch(String query, Double maxScore, int resultCount) {
        UUID currentUserId = SecurityUtils.getCurrentUserDetails().map(u -> u.getId()).orElse(null);
        Long departmentId = null;
        if (currentUserId != null) {
            departmentId = userRepository.findById(currentUserId)
                    .map(User::getDepartment)
                    .map(d -> d.getId())
                    .orElse(null);
        }
        indexingJobService.enqueueSearchLogJob(currentUserId, departmentId, query, maxScore, resultCount);
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list != null ? list : List.of();
    }

    private static double toPercent(double score) {
        return Math.round(Math.min(1.0, Math.max(0.0, score)) * 1000) / 10.0;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
