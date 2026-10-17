package vn.edu.ptit.library.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.catalog.entity.PhysicalCopy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PhysicalCopyRepository extends JpaRepository<PhysicalCopy, UUID> {

    Optional<PhysicalCopy> findByBarcode(String barcode);

    List<PhysicalCopy> findByDocumentId(UUID documentId);

    long countByDocumentIdAndStatus(UUID documentId, String status);

    @Query("SELECT p FROM PhysicalCopy p WHERE p.document.id = :docId AND p.status = 'AVAILABLE'")
    List<PhysicalCopy> findAvailableCopiesByDocumentId(@Param("docId") UUID docId);
}
