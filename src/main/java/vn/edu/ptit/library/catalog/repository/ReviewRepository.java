package vn.edu.ptit.library.catalog.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.catalog.entity.Review;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByUserIdAndDocumentId(UUID userId, UUID documentId);

    Page<Review> findByDocumentId(UUID documentId, Pageable pageable);

    @Query(
            value = "SELECT r FROM Review r JOIN FETCH r.user WHERE r.document.id = :documentId",
            countQuery = "SELECT COUNT(r) FROM Review r WHERE r.document.id = :documentId"
    )
    Page<Review> findByDocumentIdWithUser(@Param("documentId") UUID documentId, Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.document.id = :documentId")
    Double getAverageRatingForDocument(@Param("documentId") UUID documentId);

    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.document.id = :documentId GROUP BY r.rating")
    List<Object[]> countRatingDistributionByDocumentId(@Param("documentId") UUID documentId);

    @Query("SELECT r.document.id, AVG(r.rating) FROM Review r WHERE r.document.id IN :documentIds GROUP BY r.document.id")
    List<Object[]> getAverageRatingsByDocumentIds(@Param("documentIds") Collection<UUID> documentIds);

    long countByDocumentId(UUID documentId);
}
