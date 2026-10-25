package vn.edu.ptit.library.circulation.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.circulation.entity.Circulation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CirculationRepository extends JpaRepository<Circulation, Long> {

    List<Circulation> findByUserIdAndReturnDateIsNull(UUID userId);

    long countByUserIdAndReturnDateIsNull(UUID userId);

    Page<Circulation> findByUserId(UUID userId, Pageable pageable);

    Page<Circulation> findByUserIdAndReturnDateIsNull(UUID userId, Pageable pageable);

    Page<Circulation> findByUserIdAndReturnDateIsNotNull(UUID userId, Pageable pageable);

    @Query("SELECT c FROM Circulation c WHERE c.user.id = :userId AND c.returnDate IS NULL AND c.dueDate < :now")
    Page<Circulation> findOverdueByUserId(@Param("userId") UUID userId, @Param("now") OffsetDateTime now, Pageable pageable);

    Optional<Circulation> findByPhysicalCopyIdAndReturnDateIsNull(UUID physicalCopyId);

    @Query("SELECT c FROM Circulation c WHERE c.physicalCopy.barcode = :barcode AND c.returnDate IS NULL")
    Optional<Circulation> findActiveLoanByBarcode(@Param("barcode") String barcode);

    @Query("SELECT c FROM Circulation c WHERE c.physicalCopy.document.id = :documentId AND c.returnDate IS NULL ORDER BY c.dueDate ASC")
    List<Circulation> findActiveLoansByDocumentIdOrderByDueDateAsc(@Param("documentId") UUID documentId);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Circulation c WHERE c.user.id = :userId AND c.physicalCopy.document.id = :documentId AND c.returnDate IS NULL")
    boolean isUserCurrentlyBorrowingDocument(@Param("userId") UUID userId, @Param("documentId") UUID documentId);

    @Query("SELECT c FROM Circulation c WHERE c.returnDate IS NULL AND c.dueDate < :now")
    List<Circulation> findOverdueLoans(@Param("now") OffsetDateTime now);
}
