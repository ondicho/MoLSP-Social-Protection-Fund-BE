package MoLSP.go.ke.Social_Assistance_Fund.models;

import MoLSP.go.ke.Social_Assistance_Fund.configs.JpaAuditingConfig;
import MoLSP.go.ke.Social_Assistance_Fund.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticatedAuditorTests {

    private final org.springframework.data.domain.AuditorAware<String> auditor =
            new JpaAuditingConfig().authenticatedAuditor();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedPrincipalUsesStableUserIdRatherThanEmail() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("officer@example.test");
        user.setRole(Role.DATA_ENTRY_OFFICER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        assertEquals(user.getId().toString(), auditor.getCurrentAuditor().orElseThrow());
        user.setEmail("changed@example.test");
        assertEquals(user.getId().toString(), auditor.getCurrentAuditor().orElseThrow());
        user.setEnabled(false);
        assertThrows(AuthenticationCredentialsNotFoundException.class, auditor::getCurrentAuditor);
    }

    @Test
    void absentAnonymousAndUnauthenticatedPrincipalsAreRejected() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, auditor::getCurrentAuditor);
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", java.util.List.of(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertThrows(AuthenticationCredentialsNotFoundException.class, auditor::getCurrentAuditor);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new User(), null));
        assertThrows(AuthenticationCredentialsNotFoundException.class, auditor::getCurrentAuditor);
    }
}
