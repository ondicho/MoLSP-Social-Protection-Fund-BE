package MoLSP.go.ke.Social_Assistance_Fund.repositories;

import MoLSP.go.ke.Social_Assistance_Fund.models.Applicant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApplicantRepository extends JpaRepository<Applicant, UUID> {
    Optional<Applicant> findByIdentityNumber(String identityNumber);
}
