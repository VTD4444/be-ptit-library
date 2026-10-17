package vn.edu.ptit.library.search.repository;

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
}
