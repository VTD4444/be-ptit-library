package vn.edu.ptit.library.search.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.search.entity.SearchVectorConfig;

import java.util.Optional;

@Repository
public interface SearchVectorConfigRepository extends JpaRepository<SearchVectorConfig, Long> {

    Optional<SearchVectorConfig> findByIsActiveTrue();
}
