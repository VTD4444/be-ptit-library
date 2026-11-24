package vn.edu.ptit.library.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.catalog.entity.DocumentDepartment;
import vn.edu.ptit.library.catalog.entity.DocumentDepartmentId;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentDepartmentRepository extends JpaRepository<DocumentDepartment, DocumentDepartmentId> {

    List<DocumentDepartment> findByDocumentId(UUID documentId);

    @Query("SELECT dd FROM DocumentDepartment dd JOIN FETCH dd.department WHERE dd.id.documentId = :documentId")
    List<DocumentDepartment> findByDocumentIdWithDepartment(@Param("documentId") UUID documentId);

    List<DocumentDepartment> findByDepartmentId(Long departmentId);

    void deleteByDocumentId(UUID documentId);
}
