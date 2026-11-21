package vn.edu.ptit.library.search.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.search.entity.DocumentSimilarity;
import vn.edu.ptit.library.search.entity.DocumentSimilarityId;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentSimilarityRepository extends JpaRepository<DocumentSimilarity, DocumentSimilarityId> {

    @Query("SELECT s FROM DocumentSimilarity s WHERE s.documentA.id = :docId ORDER BY s.simScore DESC")
    List<DocumentSimilarity> findTopSimilarDocuments(@Param("docId") UUID docId);

    @Query("""
            SELECT s FROM DocumentSimilarity s
            JOIN FETCH s.documentB b
            WHERE s.id.documentAId = :docId
              AND s.id.modelVersion = :modelVersion
              AND s.simScore >= :minScore
              AND b.deletedAt IS NULL
            ORDER BY s.simScore DESC
            """)
    List<DocumentSimilarity> findSimilarDocuments(
            @Param("docId") UUID docId,
            @Param("modelVersion") String modelVersion,
            @Param("minScore") double minScore,
            Pageable pageable);
}
