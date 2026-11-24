package vn.edu.ptit.library.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.user.entity.User;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    @Override
    @EntityGraph(attributePaths = "department")
    @NonNull
    Page<User> findAll(@NonNull Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "department")
    @NonNull
    Page<User> findAll(@Nullable Specification<User> spec, @NonNull Pageable pageable);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUserCode(String userCode);

    Optional<User> findByEmailOrUserCode(String email, String userCode);

    boolean existsByEmail(String email);

    boolean existsByUserCode(String userCode);

    boolean existsByUserCodeIgnoreCase(String userCode);

    long countByStatus(String status);
}
