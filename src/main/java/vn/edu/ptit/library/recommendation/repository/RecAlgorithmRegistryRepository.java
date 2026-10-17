package vn.edu.ptit.library.recommendation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.recommendation.entity.RecAlgorithmRegistry;

import java.util.Optional;

@Repository
public interface RecAlgorithmRegistryRepository extends JpaRepository<RecAlgorithmRegistry, String> {

    Optional<RecAlgorithmRegistry> findByIsActiveTrue();
}
