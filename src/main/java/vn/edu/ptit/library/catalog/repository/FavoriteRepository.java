package vn.edu.ptit.library.catalog.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.catalog.entity.Favorite;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    Optional<Favorite> findByUserIdAndDocumentId(UUID userId, UUID documentId);

    boolean existsByUserIdAndDocumentId(UUID userId, UUID documentId);

    Page<Favorite> findByUserId(UUID userId, Pageable pageable);

    long countByDocumentId(UUID documentId);
}
