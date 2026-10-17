package vn.edu.ptit.library.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.catalog.entity.DigitalCopy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DigitalCopyRepository extends JpaRepository<DigitalCopy, UUID> {

    List<DigitalCopy> findByDocumentId(UUID documentId);

    Optional<DigitalCopy> findByDocumentIdAndIsCurrentTrue(UUID documentId);

    Optional<DigitalCopy> findByFileHash(String fileHash);
}
