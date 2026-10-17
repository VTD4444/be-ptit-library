package vn.edu.ptit.library.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.library.user.entity.UserProfile;

import java.util.UUID;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    @Modifying
    @Query(value = "UPDATE public.user_profiles SET profile_embedding = CAST(:embedding AS vector), updated_at = now() WHERE user_id = :userId", nativeQuery = true)
    int updateProfileEmbedding(@Param("userId") UUID userId, @Param("embedding") String embedding);
}
