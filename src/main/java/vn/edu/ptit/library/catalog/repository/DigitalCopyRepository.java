package vn.edu.ptit.library.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.catalog.entity.DigitalCopy;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DigitalCopyRepository extends JpaRepository<DigitalCopy, UUID> {

    List<DigitalCopy> findByDocumentId(UUID documentId);

    Optional<DigitalCopy> findByDocumentIdAndIsCurrentTrue(UUID documentId);

    @Query("SELECT DISTINCT d.document.id FROM DigitalCopy d WHERE d.document.id IN :documentIds AND d.isCurrent = true")
    List<UUID> findDocumentIdsWithCurrentDigitalCopy(@Param("documentIds") Collection<UUID> documentIds);

    Optional<DigitalCopy> findByFileHash(String fileHash);

    boolean existsByDocumentIdAndFileHashAndProcessingStatusNot(UUID documentId, String fileHash, String processingStatus);
}
