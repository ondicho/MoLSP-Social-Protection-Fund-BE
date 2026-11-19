package MoLSP.go.ke.Social_Assistance_Fund.repositories;

import MoLSP.go.ke.Social_Assistance_Fund.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);

    @Query(value = """
            SELECT COUNT(*) FROM users
            WHERE phone_number = :phoneNumber AND (:id IS NULL OR id <> :id)
            """, nativeQuery = true)
    long countByPhoneNumberExcludingId(@Param("phoneNumber") String phoneNumber, @Param("id") UUID id);
    long countByRoleAndEnabledTrue(MoLSP.go.ke.Social_Assistance_Fund.enums.Role role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.role = :role and user.enabled = true")
    List<User> findEnabledByRoleForUpdate(@Param("role") MoLSP.go.ke.Social_Assistance_Fund.enums.Role role);
}
