package MoLSP.go.ke.Social_Assistance_Fund.models;

import MoLSP.go.ke.Social_Assistance_Fund.configs.JpaAuditingConfig;
import MoLSP.go.ke.Social_Assistance_Fund.controllers.GeographyController;
import MoLSP.go.ke.Social_Assistance_Fund.enums.Role;
import MoLSP.go.ke.Social_Assistance_Fund.repositories.UserRepository;
import MoLSP.go.ke.Social_Assistance_Fund.utils.GeographySeeder;
import MoLSP.go.ke.Social_Assistance_Fund.configs.GeographyProperties;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class JpaAuditingTests {

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
    }

    @Autowired
    UserRepository users;
    @Autowired
    TransactionTemplate transactions;
    @Autowired
    EntityManager entityManager;
    @Autowired
    JpaAuditingConfig auditing;
    @Autowired
    GeographySeeder geographySeeder;
    @Autowired
    GeographyProperties geography;
    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired
    org.springframework.web.context.WebApplicationContext webContext;
    @Autowired
    MoLSP.go.ke.Social_Assistance_Fund.security.JwtProvider jwtProvider;

    @Test
    void geographySeedIsCompleteLinkedAndIdempotent() throws Exception {
        assertEquals(47, jdbc.queryForObject("SELECT COUNT(*) FROM counties", Integer.class));
        assertEquals(333, jdbc.queryForObject("SELECT COUNT(*) FROM sub_counties", Integer.class));
        assertEquals(47, geography.counties().size());
        Integer nairobi = jdbc.queryForObject("SELECT id FROM counties WHERE name = 'Nairobi'", Integer.class);
        assertEquals(47, nairobi);
        assertEquals(11, jdbc.queryForObject("SELECT COUNT(*) FROM sub_counties WHERE county_id = ?",
                Integer.class, nairobi));
        Integer id = jdbc.queryForObject(
                "SELECT id FROM sub_counties WHERE county_id = 47 AND name = 'Lang''ata'", Integer.class);
        assertNotNull(id);
        assertEquals(197489, jdbc.queryForObject(
                "SELECT population_2019 FROM sub_counties WHERE id = ?", Integer.class, id));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sub_counties WHERE name LIKE '%National Park%'", Integer.class));
        geographySeeder.run(null);
        assertEquals(333, jdbc.queryForObject("SELECT COUNT(*) FROM sub_counties", Integer.class));
        assertEquals(id, jdbc.queryForObject(
                "SELECT id FROM sub_counties WHERE county_id = 47 AND name = 'Lang''ata'", Integer.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO sub_counties (county_id, name, population_2009, population_2019) VALUES (999, 'Invalid', 0, 0)"));
    }

    @Test
    void geographyApiListsCountyScopedSubCounties() throws Exception {
        var controller = new GeographyController(
                jdbc, new MoLSP.go.ke.Social_Assistance_Fund.utils.ResponseHandler());
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        assertEquals(47, controller.counties(request).getBody().getData().size());
        var rows = controller.subCounties(47, request).getBody().getData();
        assertEquals(11, rows.size());
        assertTrue(rows.stream().allMatch(row -> row.countyId() == 47));
        assertThrows(MoLSP.go.ke.Social_Assistance_Fund.exceptions.ResourceNotFoundException.class,
                () -> controller.subCounties(999, request));
    }

    @Test
    void invalidSeedIsRejectedBeforeWriting() {
        var invalid = new GeographySeeder(new GeographyProperties(java.util.List.of()), jdbc, transactions);
        assertThrows(IllegalStateException.class, () -> invalid.run(null));
        assertEquals(47, jdbc.queryForObject("SELECT COUNT(*) FROM counties", Integer.class));
    }

    @Test
    void seedRollsBackOnExistingCountyIdentityConflict() {
        jdbc.update("UPDATE counties SET population_2009 = 1 WHERE id = 30");
        jdbc.update("UPDATE counties SET name = 'Bomet temporary' WHERE id = 36");
        jdbc.update("INSERT INTO counties (id, name, population_2009, population_2019) VALUES (99, 'Bomet', 0, 0)");
        try {
            assertThrows(IllegalStateException.class, () -> geographySeeder.run(null));
            assertEquals(1, jdbc.queryForObject("SELECT population_2009 FROM counties WHERE id = 30", Integer.class));
        } finally {
            jdbc.update("DELETE FROM counties WHERE id = 99");
            jdbc.update("UPDATE counties SET name = 'Bomet' WHERE id = 36");
            geographySeeder.run(null);
        }
    }

    @Test
    void geographyHttpEndpointsRequireAuthenticationAndReturnScopedResults() throws Exception {
        User actor = newUser();
        authenticate();
        transactions.executeWithoutResult(status -> users.saveAndFlush(actor));
        String bearer = "Bearer " + jwtProvider.generateAccessToken(actor.getId());
        SecurityContextHolder.clearContext();
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/geography/counties"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/geography/counties/47/sub-counties").header("Authorization", bearer))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.length()").value(11))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data[0].countyId").value(47));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/geography/counties/999/sub-counties").header("Authorization", bearer))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void everyMappedEntityInheritsBase() {
        entityManager.getMetamodel().getEntities().forEach(entity ->
                assertTrue(Base.class.isAssignableFrom(entity.getJavaType()),
                        entity.getName() + " must extend Base"));
    }

    @Test
    void listenerPopulatesInheritedFieldsAndPreservesCreatorOnUpdate() {
        UUID creator = authenticate();
        User inserted = transactions.execute(status -> users.saveAndFlush(newUser()));
        User saved = users.findById(inserted.getId()).orElseThrow();
        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertEquals(saved.getCreatedAt(), saved.getUpdatedAt());
        assertEquals(creator.toString(), saved.getCreatedBy());
        assertEquals(creator.toString(), saved.getUpdatedBy());
        assertEquals("+254712345678", saved.getPhoneNumber());

        UUID editor = authenticate();
        User updated = transactions.execute(status -> {
            User user = users.findById(saved.getId()).orElseThrow();
            user.setFullName("Updated name");
            users.flush();
            return user;
        });
        assertEquals(saved.getCreatedAt(), updated.getCreatedAt());
        assertEquals(creator.toString(), updated.getCreatedBy());
        assertEquals(editor.toString(), updated.getUpdatedBy());
        assertFalse(updated.getUpdatedAt().isBefore(saved.getUpdatedAt()));
        assertEquals(saved.getVersion() + 1, updated.getVersion());
    }

    @Test
    void nullableMiddleNamePersistsAndInvalidPhoneUpdateRollsBack() {
        authenticate();
        User user = newUser();
        user.setMiddleName(null);
        User saved = transactions.execute(status -> users.saveAndFlush(user));
        assertNull(users.findById(saved.getId()).orElseThrow().getMiddleName());
        assertThrows(RuntimeException.class, () -> transactions.executeWithoutResult(status -> {
            User existing = users.findById(saved.getId()).orElseThrow();
            existing.setPhoneNumber("+12025550123");
            users.flush();
        }));
        assertEquals("+254712345678", users.findById(saved.getId()).orElseThrow().getPhoneNumber());
    }

    @Test
    void unauthenticatedInsertAndUpdateAreRolledBack() {
        long count = users.count();
        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> transactions.execute(status -> users.saveAndFlush(newUser())));
        assertInstanceOf(AuthenticationCredentialsNotFoundException.class,
                org.springframework.core.NestedExceptionUtils.getMostSpecificCause(failure));
        assertEquals(count, users.count());

        authenticate();
        User saved = transactions.execute(status -> users.saveAndFlush(newUser()));
        SecurityContextHolder.clearContext();
        assertThrows(RuntimeException.class, () -> transactions.execute(status -> {
            User user = users.findById(saved.getId()).orElseThrow();
            user.setFullName("Must roll back");
            users.flush();
            return null;
        }));
        assertEquals(saved.getFullName(), users.findById(saved.getId()).orElseThrow().getFullName());
    }

    @Test
    void anonymousAndUntrustedPrincipalsCannotSupplyAnAuditor() {
        var auditor = auditing.authenticatedAuditor();
        assertThrows(AuthenticationCredentialsNotFoundException.class, auditor::getCurrentAuditor);
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertThrows(AuthenticationCredentialsNotFoundException.class, auditor::getCurrentAuditor);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("untrusted", null, java.util.List.of()));
        assertThrows(AuthenticationCredentialsNotFoundException.class, auditor::getCurrentAuditor);
        authenticate();
        User actor = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        actor.setEnabled(false);
        assertThrows(AuthenticationCredentialsNotFoundException.class, auditor::getCurrentAuditor);
    }

    @Test
    void softDeletedUsersAreNotLoaded() {
        authenticate();
        User saved = transactions.execute(status -> users.saveAndFlush(newUser()));
        transactions.executeWithoutResult(status -> {
            User user = users.findById(saved.getId()).orElseThrow();
            user.setDeletedAt(java.time.Instant.now());
            user.setDeletedBy(user.getCreatedBy());
            users.flush();
            entityManager.clear();
        });
        assertTrue(users.findById(saved.getId()).isEmpty());
    }

    private UUID authenticate() {
        User actor = newUser();
        actor.setId(UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, actor.getAuthorities()));
        return actor.getId();
    }

    private User newUser() {
        User user = new User("Jane", "Mary", "Doe");
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPasswordHash("test-only-hash");
        user.setRole(Role.DATA_ENTRY_OFFICER);
        user.setPhoneNumber("0712345678");
        return user;
    }
}
