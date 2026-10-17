package vn.edu.ptit.library.circulation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.circulation.entity.LoanPolicy;

import java.util.Optional;

@Repository
public interface LoanPolicyRepository extends JpaRepository<LoanPolicy, Long> {

    Optional<LoanPolicy> findByUserTypeAndDocType(String userType, String docType);
}
