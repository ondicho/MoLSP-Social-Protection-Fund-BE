package MoLSP.go.ke.Social_Assistance_Fund.repositories;

import MoLSP.go.ke.Social_Assistance_Fund.models.SocialFund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface SocialFundRepository extends JpaRepository<SocialFund, UUID>, JpaSpecificationExecutor<SocialFund> {
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, UUID id);
}
