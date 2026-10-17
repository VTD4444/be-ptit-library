package vn.edu.ptit.library.circulation.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.circulation.entity.CirculationFine;

import java.util.List;

@Repository
public interface CirculationFineRepository extends JpaRepository<CirculationFine, Long> {

    List<CirculationFine> findByCirculationId(Long circulationId);

    Page<CirculationFine> findByStatus(String status, Pageable pageable);
}
