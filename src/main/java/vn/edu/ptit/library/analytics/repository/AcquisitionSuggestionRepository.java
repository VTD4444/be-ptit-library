package vn.edu.ptit.library.analytics.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.analytics.entity.AcquisitionSuggestion;

import java.util.List;

@Repository
public interface AcquisitionSuggestionRepository extends JpaRepository<AcquisitionSuggestion, Long> {

    List<AcquisitionSuggestion> findByDepartmentId(Long departmentId);

    Page<AcquisitionSuggestion> findByStatus(String status, Pageable pageable);

    long countByStatus(String status);
}
