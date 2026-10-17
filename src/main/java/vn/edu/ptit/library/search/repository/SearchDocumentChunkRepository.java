package vn.edu.ptit.library.search.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.search.entity.SearchDocumentChunk;
import vn.edu.ptit.library.search.repository.projection.ChunkSearchResultProjection;

import java.util.List;
import java.util.UUID;

@Repository
public interface SearchDocumentChunkRepository extends JpaRepository<SearchDocumentChunk, UUID> {

    List<SearchDocumentChunk> findByDigitalCopyId(UUID digitalCopyId);

    List<SearchDocumentChunk> findByDocumentId(UUID documentId);

    long countByDocumentId(UUID documentId);

    /**
     * Truy van tim kiem ngu nghia (Semantic Vector Search) bang toan tu khoang cach cosine <=> cua pgvector.
     * Cú pháp CAST(:queryVector AS vector) duoc su dung de tranh loi named parameter cua Spring Data JPA voi dau '::'.
     */
    @Query(value = """
            SELECT c.id AS id,
                   c.document_id AS documentId,
                   d.title AS title,
                   c.chunk_text AS chunkText,
                   c.page_number AS pageNumber,
                   c.chapter AS chapter,
                   c.section AS section,
                   (1 - (c.embedding <=> CAST(:queryVector AS vector))) AS semanticScore
            FROM public.search_document_chunks c
            JOIN public.documents d ON c.document_id = d.id
            WHERE (:configId IS NULL OR c.search_config_id = :configId)
              AND d.deleted_at IS NULL
            ORDER BY c.embedding <=> CAST(:queryVector AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<ChunkSearchResultProjection> searchByCosineSimilarity(
            @Param("queryVector") String queryVector,
            @Param("configId") Long configId,
            @Param("limit") int limit
    );

    /**
     * Truy van tim kiem toan van (Full-Text Search) su dung chi muc nguoc fts_tokens cua PostgreSQL.
     */
    @Query(value = """
            SELECT c.id AS id,
                   c.document_id AS documentId,
                   d.title AS title,
                   c.chunk_text AS chunkText,
                   c.page_number AS pageNumber,
                   c.chapter AS chapter,
                   c.section AS section,
                   ts_rank(c.fts_tokens, plainto_tsquery('simple', :keyword)) AS semanticScore
            FROM public.search_document_chunks c
            JOIN public.documents d ON c.document_id = d.id
            WHERE c.fts_tokens @@ plainto_tsquery('simple', :keyword)
              AND d.deleted_at IS NULL
            ORDER BY semanticScore DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<ChunkSearchResultProjection> searchByFullText(
            @Param("keyword") String keyword,
            @Param("limit") int limit
    );
}
