package vn.edu.ptit.library.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.catalog.entity.DocumentAuthor;
import vn.edu.ptit.library.catalog.entity.DocumentAuthorId;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentAuthorRepository extends JpaRepository<DocumentAuthor, DocumentAuthorId> {

    List<DocumentAuthor> findByDocumentIdOrderByAuthorOrderAsc(UUID documentId);

    @Query("SELECT da FROM DocumentAuthor da JOIN FETCH da.author WHERE da.id.documentId IN :documentIds ORDER BY da.id.documentId ASC, da.authorOrder ASC")
    List<DocumentAuthor> findByDocumentIdInWithAuthor(@Param("documentIds") Collection<UUID> documentIds);

    long countByAuthorId(Long authorId);

    void deleteByDocumentId(UUID documentId);
}
