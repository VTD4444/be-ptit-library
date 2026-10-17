package vn.edu.ptit.library.catalog.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.catalog.entity.Review;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByUserIdAndDocumentId(UUID userId, UUID documentId);

    Page<Review> findByDocumentId(UUID documentId, Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.document.id = :documentId")
    Double getAverageRatingForDocument(@Param("documentId") UUID documentId);

    long countByDocumentId(UUID documentId);
}
