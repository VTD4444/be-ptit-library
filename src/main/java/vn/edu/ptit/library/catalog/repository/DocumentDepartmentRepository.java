package vn.edu.ptit.library.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.catalog.entity.DocumentDepartment;
import vn.edu.ptit.library.catalog.entity.DocumentDepartmentId;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentDepartmentRepository extends JpaRepository<DocumentDepartment, DocumentDepartmentId> {

    List<DocumentDepartment> findByDocumentId(UUID documentId);

    List<DocumentDepartment> findByDepartmentId(Long departmentId);

    void deleteByDocumentId(UUID documentId);
}
