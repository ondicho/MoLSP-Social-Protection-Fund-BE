package MoLSP.go.ke.Social_Assistance_Fund.configs;

import MoLSP.go.ke.Social_Assistance_Fund.models.User;
import MoLSP.go.ke.Social_Assistance_Fund.utils.PublicRegistrationAuditContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "authenticatedAuditor")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> authenticatedAuditor() {
        return () -> {
            if (PublicRegistrationAuditContext.isActive()) {
                return Optional.of(PublicRegistrationAuditContext.ACTOR);
            }
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()
                    || new AuthenticationTrustResolverImpl().isAnonymous(authentication)
                    || !(authentication.getPrincipal() instanceof User user)
                    || user.getId() == null || !user.isEnabled()) {
                throw new AuthenticationCredentialsNotFoundException(
                        "An authenticated user is required for persistence auditing");
            }
            return Optional.of(user.getId().toString());
        };
    }
}
