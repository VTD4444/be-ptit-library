package vn.edu.ptit.library.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.user.entity.User;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUserCode(String userCode);

    Optional<User> findByEmailOrUserCode(String email, String userCode);

    boolean existsByEmail(String email);

    boolean existsByUserCode(String userCode);
}
