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
import java.util.UUID;

@Repository
public interface CirculationRepository extends JpaRepository<Circulation, Long> {

    List<Circulation> findByUserIdAndReturnDateIsNull(UUID userId);

    long countByUserIdAndReturnDateIsNull(UUID userId);

    Page<Circulation> findByUserId(UUID userId, Pageable pageable);

    @Query("SELECT c FROM Circulation c WHERE c.returnDate IS NULL AND c.dueDate < :now")
    List<Circulation> findOverdueLoans(@Param("now") OffsetDateTime now);
}
