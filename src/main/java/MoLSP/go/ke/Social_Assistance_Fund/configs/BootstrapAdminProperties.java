package MoLSP.go.ke.Social_Assistance_Fund.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap-admin")
public record BootstrapAdminProperties(String firstName, String lastName, String email, String password) {
}
