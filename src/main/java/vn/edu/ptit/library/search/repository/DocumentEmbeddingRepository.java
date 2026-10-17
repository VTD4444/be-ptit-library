package vn.edu.ptit.library.search.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.search.entity.DocumentEmbedding;

import java.util.UUID;

@Repository
public interface DocumentEmbeddingRepository extends JpaRepository<DocumentEmbedding, UUID> {

    @Modifying
    @Query(value = "UPDATE public.document_embeddings SET embedding = CAST(:embedding AS vector), model_version = :modelVersion, updated_at = now() WHERE document_id = :docId", nativeQuery = true)
    int updateEmbedding(@Param("docId") UUID docId, @Param("embedding") String embedding, @Param("modelVersion") String modelVersion);
}
