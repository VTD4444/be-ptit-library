package vn.edu.ptit.library.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.catalog.entity.DocumentAuthor;
import vn.edu.ptit.library.catalog.entity.DocumentAuthorId;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentAuthorRepository extends JpaRepository<DocumentAuthor, DocumentAuthorId> {

    List<DocumentAuthor> findByDocumentIdOrderByAuthorOrderAsc(UUID documentId);

    long countByAuthorId(Long authorId);

    void deleteByDocumentId(UUID documentId);
}
