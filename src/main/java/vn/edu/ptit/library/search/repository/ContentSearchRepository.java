package vn.edu.ptit.library.search.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Truy van phuc vu tim kiem noi dung tren search_document_chunks.
 * Tim kiem TF-IDF + dinh vi chay o Python Worker; o day chi loc tai lieu, lay thong tin hien thi va Full-Text Search du phong.
 * SQL dung dong vi cac bo loc deu la tuy chon.
 */
@Repository
@RequiredArgsConstructor
public class ContentSearchRepository {

    private static final String CHUNK_COLUMNS = """
            c.id AS chunk_id, c.document_id, c.page_number, c.page_end, c.printed_page_label,
            c.chapter, c.section, c.chunk_text, d.title, d.doc_type, d.language, d.publication_year""";

    private static final RowMapper<ChunkRow> CHUNK_ROW_MAPPER = (rs, rowNum) -> new ChunkRow(
            rs.getObject("chunk_id", UUID.class),
            rs.getObject("document_id", UUID.class),
            rs.getString("title"),
            rs.getString("doc_type"),
            rs.getString("language"),
            rs.getObject("publication_year", Integer.class),
            rs.getObject("page_number", Integer.class),
            rs.getObject("page_end", Integer.class),
            rs.getString("printed_page_label"),
            rs.getString("chapter"),
            rs.getString("section"),
            rs.getString("chunk_text"));

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public record DocumentFilter(String language, String docType, Long departmentId) {
        public boolean isEmpty() {
            return language == null && docType == null && departmentId == null;
        }
    }

    public record ChunkRow(
            UUID chunkId,
            UUID documentId,
            String title,
            String docType,
            String language,
            Integer publicationYear,
            Integer pageNumber,
            Integer pageEnd,
            String printedPageLabel,
            String chapter,
            String section,
            String chunkText) {
    }

    public record ScoredChunk(ChunkRow chunk, double score) {
    }

    public record DocumentSummary(String title, String docType, String language, Integer publicationYear) {
    }

    /**
     * ID cac tai lieu co ban so hien hanh thoa man bo loc (ngon ngu, dang tai lieu, nganh hoc).
     */
    public List<UUID> findDocumentIds(DocumentFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT d.id FROM public.documents d WHERE d.deleted_at IS NULL"
                + " AND EXISTS (SELECT 1 FROM public.digital_copies dc WHERE dc.document_id = d.id AND dc.is_current = true)"
                + documentFilterSql(filter, params);
        return jdbcTemplate.queryForList(sql, params, UUID.class);
    }

    /**
     * Thong tin hien thi cua cac tai lieu; bo qua tai lieu da xoa hoac ban so khong con la ban hien hanh.
     */
    public Map<UUID, DocumentSummary> findDocumentSummaries(Collection<UUID> documentIds, Collection<UUID> digitalCopyIds) {
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("documentIds", documentIds)
                .addValue("copyIds", digitalCopyIds);
        Map<UUID, DocumentSummary> result = new HashMap<>();
        jdbcTemplate.query("""
                SELECT d.id, d.title, d.doc_type, d.language, d.publication_year
                FROM public.documents d
                WHERE d.id IN (:documentIds) AND d.deleted_at IS NULL
                  AND EXISTS (SELECT 1 FROM public.digital_copies dc
                              WHERE dc.document_id = d.id AND dc.is_current = true AND dc.id IN (:copyIds))
                """, params, rs -> {
            UUID id = rs.getObject("id", UUID.class);
            result.put(id, new DocumentSummary(rs.getString("title"), rs.getString("doc_type"),
                    rs.getString("language"), rs.getObject("publication_year", Integer.class)));
        });
        return result;
    }

    /**
     * Full-Text Search du phong khi Python Worker khong san sang. Diem ts_rank_cd chuan hoa 32 nam trong [0, 1).
     */
    public List<ScoredChunk> searchFullText(String tsQuery, Long configId, UUID documentId, DocumentFilter filter,
                                            boolean bestChunkPerDocument, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tsq", tsQuery)
                .addValue("limit", limit);
        StringBuilder where = new StringBuilder(" AND c.fts_tokens @@ to_tsquery('simple', :tsq)");
        if (configId != null) {
            where.append(" AND c.search_config_id = :configId");
            params.addValue("configId", configId);
        } else {
            where.append(" AND c.search_config_id IS NULL");
        }
        if (documentId != null) {
            where.append(" AND c.document_id = :documentId");
            params.addValue("documentId", documentId);
        }
        where.append(documentFilterSql(filter, params));

        String ranked = """
                SELECT %s, ts_rank_cd(c.fts_tokens, to_tsquery('simple', :tsq), 32) AS score
                FROM public.search_document_chunks c
                JOIN public.documents d ON d.id = c.document_id AND d.deleted_at IS NULL
                JOIN public.digital_copies dc ON dc.id = c.digital_copy_id AND dc.is_current = true
                WHERE 1 = 1%s
                """.formatted(CHUNK_COLUMNS, where);
        String sql = bestChunkPerDocument
                ? "SELECT * FROM (SELECT DISTINCT ON (r.document_id) * FROM (" + ranked + ") r"
                  + " ORDER BY r.document_id, r.score DESC) b ORDER BY b.score DESC LIMIT :limit"
                : "SELECT * FROM (" + ranked + ") r ORDER BY r.score DESC LIMIT :limit";

        return jdbcTemplate.query(sql, params,
                (rs, rowNum) -> new ScoredChunk(CHUNK_ROW_MAPPER.mapRow(rs, rowNum), rs.getDouble("score")));
    }

    private static String documentFilterSql(DocumentFilter filter, MapSqlParameterSource params) {
        if (filter == null) {
            return "";
        }
        StringBuilder sql = new StringBuilder();
        if (filter.language() != null) {
            sql.append(" AND lower(d.language) = lower(:language)");
            params.addValue("language", filter.language());
        }
        if (filter.docType() != null) {
            sql.append(" AND lower(d.doc_type) = lower(:docType)");
            params.addValue("docType", filter.docType());
        }
        if (filter.departmentId() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM public.document_departments dd"
                    + " WHERE dd.document_id = d.id AND dd.department_id = :departmentId)");
            params.addValue("departmentId", filter.departmentId());
        }
        return sql.toString();
    }
}
