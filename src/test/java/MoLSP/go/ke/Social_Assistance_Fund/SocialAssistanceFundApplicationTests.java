package MoLSP.go.ke.Social_Assistance_Fund;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest
class SocialAssistanceFundApplicationTests {

	private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		MYSQL.start();
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
		registry.add("jwt.secret", () -> java.util.Base64.getEncoder()
				.encodeToString(io.jsonwebtoken.Jwts.SIG.HS256.key().build().getEncoded()));
		registry.add("app.bootstrap-admin.first-name", () -> "Bootstrap");
		registry.add("app.bootstrap-admin.last-name", () -> "Admin");
		registry.add("app.bootstrap-admin.email", () -> "bootstrap@example.test");
		registry.add("app.bootstrap-admin.password", () -> "Test-Password-987!");
	}

	@Test
	void contextLoads() {
	}

}
