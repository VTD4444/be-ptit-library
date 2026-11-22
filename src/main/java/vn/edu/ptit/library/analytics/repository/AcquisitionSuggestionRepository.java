package vn.edu.ptit.library.analytics.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.analytics.entity.AcquisitionSuggestion;

import java.util.List;

@Repository
public interface AcquisitionSuggestionRepository extends JpaRepository<AcquisitionSuggestion, Long> {

    List<AcquisitionSuggestion> findByDepartmentId(Long departmentId);

    Page<AcquisitionSuggestion> findByStatus(String status, Pageable pageable);

    @Query(
            value = "SELECT a FROM AcquisitionSuggestion a LEFT JOIN FETCH a.department " +
                    "WHERE (:status IS NULL OR a.status = :status) " +
                    "AND (:departmentId IS NULL OR a.department.id = :departmentId)",
            countQuery = "SELECT COUNT(a) FROM AcquisitionSuggestion a " +
                    "WHERE (:status IS NULL OR a.status = :status) " +
                    "AND (:departmentId IS NULL OR a.department.id = :departmentId)"
    )
    Page<AcquisitionSuggestion> findFilteredWithDepartment(
            @Param("status") String status,
            @Param("departmentId") Long departmentId,
            Pageable pageable
    );

    long countByStatus(String status);
}
