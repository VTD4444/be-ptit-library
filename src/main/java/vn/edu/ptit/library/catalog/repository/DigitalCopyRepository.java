package vn.edu.ptit.library.catalog.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query(value = "SELECT d FROM DigitalCopy d JOIN FETCH d.document doc LEFT JOIN FETCH d.uploadedBy "
            + "WHERE d.processingStatus IN :statuses "
            + "AND (LOWER(doc.title) LIKE :keyword OR LOWER(COALESCE(d.fileName, '')) LIKE :keyword)",
            countQuery = "SELECT COUNT(d) FROM DigitalCopy d JOIN d.document doc "
                    + "WHERE d.processingStatus IN :statuses "
                    + "AND (LOWER(doc.title) LIKE :keyword OR LOWER(COALESCE(d.fileName, '')) LIKE :keyword)")
    Page<DigitalCopy> searchForProcessing(@Param("statuses") Collection<String> statuses,
                                          @Param("keyword") String keyword,
                                          Pageable pageable);

    // Bo qua ban so cua tai lieu da xoa mem (truy van con tren Document ap dung @SQLRestriction), khop voi searchForProcessing
    @Query("SELECT d.processingStatus, COUNT(d) FROM DigitalCopy d "
            + "WHERE d.document.id IN (SELECT doc.id FROM Document doc) GROUP BY d.processingStatus")
    List<Object[]> countByProcessingStatus();

    List<DigitalCopy> findByDocumentId(UUID documentId);

    Optional<DigitalCopy> findByDocumentIdAndIsCurrentTrue(UUID documentId);

    @Query("SELECT DISTINCT d.document.id FROM DigitalCopy d WHERE d.document.id IN :documentIds AND d.isCurrent = true")
    List<UUID> findDocumentIdsWithCurrentDigitalCopy(@Param("documentIds") Collection<UUID> documentIds);

    Optional<DigitalCopy> findByFileHash(String fileHash);

    boolean existsByDocumentIdAndFileHashAndProcessingStatusNot(UUID documentId, String fileHash, String processingStatus);
}
