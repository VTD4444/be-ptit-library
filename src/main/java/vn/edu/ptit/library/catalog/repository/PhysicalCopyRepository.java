package vn.edu.ptit.library.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.catalog.entity.PhysicalCopy;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PhysicalCopyRepository extends JpaRepository<PhysicalCopy, UUID> {

    Optional<PhysicalCopy> findByBarcode(String barcode);

    List<PhysicalCopy> findByDocumentId(UUID documentId);

    long countByDocumentId(UUID documentId);

    long countByDocumentIdAndStatus(UUID documentId, String status);

    long countByStatus(String status);

    @Query("SELECT p.status, COUNT(p) FROM PhysicalCopy p GROUP BY p.status")
    List<Object[]> countGroupedByStatus();

    @Query("SELECT p.document.id, COUNT(p), SUM(CASE WHEN p.status = 'AVAILABLE' THEN 1L ELSE 0L END) FROM PhysicalCopy p WHERE p.document.id IN :documentIds GROUP BY p.document.id")
    List<Object[]> countCopiesByDocumentIds(@Param("documentIds") Collection<UUID> documentIds);

    @Query("SELECT p FROM PhysicalCopy p WHERE p.document.id = :docId AND p.status = 'AVAILABLE'")
    List<PhysicalCopy> findAvailableCopiesByDocumentId(@Param("docId") UUID docId);
}
