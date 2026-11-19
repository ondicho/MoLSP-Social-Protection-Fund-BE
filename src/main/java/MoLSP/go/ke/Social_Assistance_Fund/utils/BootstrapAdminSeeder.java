package MoLSP.go.ke.Social_Assistance_Fund.utils;

import MoLSP.go.ke.Social_Assistance_Fund.configs.BootstrapAdminProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Component
@EnableConfigurationProperties(BootstrapAdminProperties.class)
@RequiredArgsConstructor
@Slf4j
public class BootstrapAdminSeeder implements ApplicationRunner {
    private final BootstrapAdminProperties properties;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactions;

    @Override
    public void run(ApplicationArguments args) {
        String firstName = required(properties.firstName(), "PLATFORM_ADMIN_FIRST_NAME");
        String lastName = required(properties.lastName(), "PLATFORM_ADMIN_LAST_NAME");
        String email = required(properties.email(), "PLATFORM_ADMIN_EMAIL").toLowerCase(Locale.ROOT);
        String password = required(properties.password(), "PLATFORM_ADMIN_PASSWORD");
        if (firstName.length() > 200 || lastName.length() > 200
                || firstName.strip().length() + lastName.strip().length() + 1 > 200
                || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || email.length() > 255
                || password.length() < 12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Platform administrator bootstrap configuration is invalid");
        }

        transactions.executeWithoutResult(status -> seed(firstName, lastName, email, password));
    }

    private void seed(String firstName, String lastName, String email, String password) {
        var existingRoles = jdbc.query(
                "SELECT role_code FROM users WHERE email = ?",
                (rs, row) -> rs.getString("role_code"), email);
        if (!existingRoles.isEmpty()) {
            if (existingRoles.size() != 1 || !"PLATFORM_ADMIN".equals(existingRoles.getFirst())) {
                throw new IllegalStateException("Configured bootstrap email belongs to a non-platform-admin account");
            }
            log.info("event=platform_admin_bootstrap status=already_present");
            return;
        }

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String fullName = firstName.strip() + " " + lastName.strip();
        jdbc.update("""
                INSERT INTO users
                    (id, email, full_name, first_name, last_name, password_hash, enabled, role_code,
                     version, created_at, updated_at, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, TRUE, 'PLATFORM_ADMIN', 0, ?, ?, ?, ?)
                """,
                uuidBytes(id), email, fullName, firstName.strip(), lastName.strip(),
                passwordEncoder.encode(password), now, now, id.toString(), id.toString());
        log.info("event=platform_admin_bootstrap status=created");
    }

    private byte[] uuidBytes(UUID id) {
        return ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }

    private String required(String value, String variable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variable + " must be configured");
        }
        return value.strip();
    }
}
