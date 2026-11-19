package MoLSP.go.ke.Social_Assistance_Fund.repositories;

import MoLSP.go.ke.Social_Assistance_Fund.models.SocialAssistanceApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface SocialAssistanceApplicationRepository extends JpaRepository<SocialAssistanceApplication, UUID>,
        JpaSpecificationExecutor<SocialAssistanceApplication> {
}
