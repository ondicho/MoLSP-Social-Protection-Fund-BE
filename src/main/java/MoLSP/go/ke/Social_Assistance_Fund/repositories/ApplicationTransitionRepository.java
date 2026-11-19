package MoLSP.go.ke.Social_Assistance_Fund.repositories;

import MoLSP.go.ke.Social_Assistance_Fund.models.ApplicationTransition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApplicationTransitionRepository extends JpaRepository<ApplicationTransition, UUID> {
    List<ApplicationTransition> findByApplicationIdOrderByOccurredAtAsc(UUID applicationId);
}
