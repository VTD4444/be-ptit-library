package vn.edu.ptit.library.circulation.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.circulation.entity.CirculationFine;

import java.util.List;
import java.util.UUID;

@Repository
public interface CirculationFineRepository extends JpaRepository<CirculationFine, Long> {

    List<CirculationFine> findByCirculationId(Long circulationId);

    Page<CirculationFine> findByStatus(String status, Pageable pageable);

    @Query("SELECT COALESCE(SUM(cf.fineAmount), 0) FROM CirculationFine cf WHERE cf.circulation.user.id = :userId AND UPPER(cf.status) = 'UNPAID'")
    Long calculateTotalUnpaidFineByUserId(@Param("userId") UUID userId);
}
