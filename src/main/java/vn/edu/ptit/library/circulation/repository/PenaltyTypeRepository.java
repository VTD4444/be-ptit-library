package vn.edu.ptit.library.circulation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.circulation.entity.PenaltyType;

import java.util.Optional;

@Repository
public interface PenaltyTypeRepository extends JpaRepository<PenaltyType, Long> {

    Optional<PenaltyType> findByCode(String code);
}
