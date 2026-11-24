package vn.edu.ptit.library.circulation.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.circulation.entity.CirculationFine;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CirculationFineRepository extends JpaRepository<CirculationFine, Long> {

    List<CirculationFine> findByCirculationId(Long circulationId);

    Page<CirculationFine> findByStatus(String status, Pageable pageable);

    @Query("SELECT COALESCE(SUM(cf.fineAmount), 0) FROM CirculationFine cf WHERE cf.circulation.user.id = :userId AND UPPER(cf.status) = 'UNPAID'")
    Long calculateTotalUnpaidFineByUserId(@Param("userId") UUID userId);

    @Query("SELECT f FROM CirculationFine f "
            + "JOIN FETCH f.circulation c "
            + "JOIN FETCH c.physicalCopy pc "
            + "JOIN FETCH pc.document "
            + "LEFT JOIN FETCH f.penaltyType "
            + "WHERE c.user.id = :userId ORDER BY f.createdAt DESC")
    List<CirculationFine> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    @Query("SELECT f FROM CirculationFine f WHERE f.circulation.user.id = :userId AND f.status = :status ORDER BY f.createdAt DESC")
    List<CirculationFine> findByUserIdAndStatusOrderByCreatedAtDesc(@Param("userId") UUID userId, @Param("status") String status);

    @Query("SELECT COALESCE(SUM(f.fineAmount), 0) FROM CirculationFine f WHERE f.circulation.id = :circulationId")
    Long sumFineAmountByCirculationId(@Param("circulationId") Long circulationId);

    @Query("SELECT f.circulation.id, COALESCE(SUM(f.fineAmount), 0) FROM CirculationFine f WHERE f.circulation.id IN :circulationIds GROUP BY f.circulation.id")
    List<Object[]> sumFineAmountsByCirculationIds(@Param("circulationIds") Collection<Long> circulationIds);
}
