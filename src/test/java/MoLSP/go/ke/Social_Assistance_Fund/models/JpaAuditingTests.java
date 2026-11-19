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
        registry.add("app.bootstrap-admin.first-name", () -> "Bootstrap");
        registry.add("app.bootstrap-admin.last-name", () -> "Admin");
        registry.add("app.bootstrap-admin.email", () -> "bootstrap@example.test");
        registry.add("app.bootstrap-admin.password", () -> "Test-Password-987!");
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
    @Autowired
    org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Test
    void bootstrapPlatformAdminIsSeededIdempotentlyWithHashedPassword() {
        User bootstrap = users.findByEmail("bootstrap@example.test").orElseThrow();
        assertEquals(Role.PLATFORM_ADMIN, bootstrap.getRole());
        assertEquals("Bootstrap", bootstrap.getFirstName());
        assertEquals("Admin", bootstrap.getLastName());
        assertTrue(passwordEncoder.matches("Test-Password-987!", bootstrap.getPassword()));

        assertDoesNotThrow(() -> new MoLSP.go.ke.Social_Assistance_Fund.utils.BootstrapAdminSeeder(
                new MoLSP.go.ke.Social_Assistance_Fund.configs.BootstrapAdminProperties(
                        "Bootstrap", "Admin", "bootstrap@example.test", "Test-Password-987!"),
                jdbc, passwordEncoder, transactions).run(null));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = 'bootstrap@example.test'", Integer.class));
    }

    @Test
    void bootstrapPasswordAuthenticatesAndIssuesARequestTrackedToken() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/login")
                        .header("X-Request-ID", "bootstrap-login-test")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"bootstrap@example.test","password":"Test-Password-987!"}
                                """))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("X-Request-ID", "bootstrap-login-test"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.requestId").value("bootstrap-login-test"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.user.role").value("PLATFORM_ADMIN"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.user.password").doesNotExist());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"bootstrap@example.test","password":"wrong"}
                                """))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void platformAdminCanManageStaffButCannotCreateApplicantAccounts() throws Exception {
        User bootstrap = users.findByEmail("bootstrap@example.test").orElseThrow();
        String adminBearer = "Bearer " + jwtProvider.generateAccessToken(bootstrap.getId());
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/admin/users")
                        .header("Authorization", adminBearer)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Amina","lastName":"Officer","email":"amina.officer@example.test",
                                 "phoneNumber":"0712345678","role":"DATA_ENTRY_OFFICER",
                                 "temporaryPassword":"Temporary-Password-987!"}
                                """))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.role").value("DATA_ENTRY_OFFICER"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.passwordHash").doesNotExist());
        User officer = users.findByEmail("amina.officer@example.test").orElseThrow();
        assertTrue(passwordEncoder.matches("Temporary-Password-987!", officer.getPassword()));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/admin/users")
                        .header("Authorization", adminBearer)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Applicant","lastName":"Person","email":"applicant@example.test",
                                 "role":"APPLICANT","temporaryPassword":"Temporary-Password-987!"}
                                """))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/admin/users/" + bootstrap.getId())
                        .header("Authorization", adminBearer))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());

        String officerBearer = "Bearer " + jwtProvider.generateAccessToken(officer.getId());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/admin/users").header("Authorization", officerBearer))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void platformAdminCanCreateUpdateAndArchiveCountyFund() throws Exception {
        User bootstrap = users.findByEmail("bootstrap@example.test").orElseThrow();
        String bearer = "Bearer " + jwtProvider.generateAccessToken(bootstrap.getId());
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();

        var created = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/social-funds")
                        .header("Authorization", bearer)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Child Support","type":"COUNTY","coverage":"PLWD",
                                 "openDate":"2026-01-01","closeDate":"2026-12-31",
                                 "minAmount":100.00,"maxAmount":1000.00,"countyIds":[30]}
                                """))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.slug").value("child-support-2026-01-01-2026-12-31"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.countyIds[0]").value(30))
                .andReturn();
        var json = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(created.getResponse().getContentAsString());
        String id = json.get("data").get("id").asString();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/social-funds/" + id)
                        .header("Authorization", bearer)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Child Support Revised","type":"COUNTY","coverage":"PLWD",
                                 "openDate":"2026-01-01","closeDate":"2026-12-31",
                                 "minAmount":100.00,"maxAmount":1200.00,"countyIds":[30],"version":0}
                                """))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.version").value(1));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/social-funds/" + id + "/archive").header("Authorization", bearer))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.archived").value(true));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/social-funds/" + id).header("Authorization", bearer))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/social-funds/" + id + "/archive").header("Authorization", bearer))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.archived").value(false));
    }

    @Test
    void publicAndAssistedApplicationsFollowRoleAndStateRules() throws Exception {
        Integer subCountyId = jdbc.queryForObject(
                "SELECT id FROM sub_counties WHERE county_id = 30 AND name = 'Baringo Central'", Integer.class);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
        String publicForm = """
                {"firstName":"Public","middleName":"Test","lastName":"Applicant","gender":"FEMALE",
                 "dateOfBirth":"1990-01-01","maritalStatus":"SINGLE","identityNumber":"PUBLIC1234",
                 "subCountyId":%d,"location":"Town","subLocation":"Central","village":"Village",
                 "postalAddress":"P.O. Box 1","physicalAddress":"Main Street",
                 "telephoneContacts":["0712345678"],"applicationDate":"2026-11-18",
                 "programmeCodes":["OVC"],"applicantSignedDate":"2026-11-19"}
                """.formatted(subCountyId);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/applications/self-registration")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/applications/self-registration")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(publicForm))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.identityNumber").doesNotExist());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM application_transitions WHERE actor_id = 'PUBLIC_SELF_REGISTRATION'",
                Integer.class));

        User officer = createTestUser(Role.DATA_ENTRY_OFFICER);
        String officerBearer = "Bearer " + jwtProvider.generateAccessToken(officer.getId());
        String assistedForm = publicForm.replace("PUBLIC1234", "ASSIST1234");
        var assisted = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/applications").header("Authorization", officerBearer)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(assistedForm))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.status").value("DRAFT"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.gender").value("FEMALE"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.age").value(36))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.countyId").value(30))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.subCountyId").value(subCountyId))
                .andReturn();
        String applicationId = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(assisted.getResponse().getContentAsString()).get("data").get("id").asString();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/applications/" + applicationId + "/submit")
                        .header("Authorization", officerBearer)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.version").value(1));

        User approver = createTestUser(Role.APPROVER);
        String approverBearer = "Bearer " + jwtProvider.generateAccessToken(approver.getId());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/applications/" + applicationId + "/review")
                        .header("Authorization", officerBearer)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/applications/" + applicationId + "/review")
                        .header("Authorization", approverBearer)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.status").value("UNDER_REVIEW"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/applications/" + applicationId + "/approve")
                        .header("Authorization", approverBearer)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":2,"officialName":"Approver Name",
                                 "officialDesignation":"Approver","officialSignatureReference":"REF-1",
                                 "officialSignedDate":"2026-11-19"}
                                """))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.status").value("APPROVED"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/applications/" + applicationId + "/review")
                        .header("Authorization", approverBearer)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"version\":3}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
    }

    private User createTestUser(Role role) {
        User bootstrap = users.findByEmail("bootstrap@example.test").orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(bootstrap, null, bootstrap.getAuthorities()));
        User user = newUser();
        user.setRole(role);
        User saved = transactions.execute(status -> users.saveAndFlush(user));
        SecurityContextHolder.clearContext();
        return saved;
    }

    @Test
    void geographySeedIsCompleteLinkedAndIdempotent() throws Exception {
        assertEquals(47, jdbc.queryForObject("SELECT COUNT(*) FROM counties", Integer.class));
        assertEquals(333, jdbc.queryForObject("SELECT COUNT(*) FROM sub_counties", Integer.class));
        assertEquals(175, geography.counties().stream()
                .mapToInt(county -> (int) county.constituencies().stream()
                        .filter(group -> county.subCounties().stream()
                                .anyMatch(subCounty -> subCounty.name().equals(group.name())))
                        .count())
                .sum());
        assertEquals(113, geography.counties().stream()
                .mapToInt(county -> (int) county.constituencies().stream()
                        .filter(group -> county.subCounties().stream()
                                .noneMatch(subCounty -> subCounty.name().equals(group.name())))
                        .count())
                .sum());
        assertEquals(854, jdbc.queryForObject("SELECT COUNT(*) FROM wards", Integer.class));
        assertEquals(854, geography.counties().stream()
                .flatMap(county -> county.constituencies().stream()
                        .filter(group -> county.subCounties().stream()
                                .anyMatch(subCounty -> subCounty.name().equals(group.name())))
                        .flatMap(group -> group.wards().stream()))
                .count());
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
        Integer baringoCentralId = jdbc.queryForObject(
                "SELECT id FROM sub_counties WHERE county_id = 30 AND name = 'Baringo Central'", Integer.class);
        assertNotNull(baringoCentralId);
        assertEquals(5, jdbc.queryForObject(
                "SELECT COUNT(*) FROM wards WHERE sub_county_id = ?", Integer.class, baringoCentralId));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM wards w
                JOIN sub_counties s ON s.id = w.sub_county_id
                WHERE s.county_id = 30 AND s.name = 'Baringo South'
                """, Integer.class));
        geographySeeder.run(null);
        assertEquals(333, jdbc.queryForObject("SELECT COUNT(*) FROM sub_counties", Integer.class));
        assertEquals(854, jdbc.queryForObject("SELECT COUNT(*) FROM wards", Integer.class));
        assertEquals(id, jdbc.queryForObject(
                "SELECT id FROM sub_counties WHERE county_id = 47 AND name = 'Lang''ata'", Integer.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO sub_counties (county_id, name, population_2009, population_2019) VALUES (999, 'Invalid', 0, 0)"));
    }

    @Test
    void geographyApiListsCountyScopedSubCounties() throws Exception {
        var controller = new GeographyController(
                new MoLSP.go.ke.Social_Assistance_Fund.services.GeographyService(
                        jdbc, new MoLSP.go.ke.Social_Assistance_Fund.utils.PaginationUtil(),
                        new MoLSP.go.ke.Social_Assistance_Fund.utils.ResponseHandler()),
                new MoLSP.go.ke.Social_Assistance_Fund.utils.ResponseHandler());
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        assertEquals(2, controller.counties(null, null, null, null, request)
                .getBody().getData().content().size());
        assertThrows(IllegalArgumentException.class,
                () -> controller.counties(0, 101, null, null, request));
        var rows = controller.subCounties(47, 0, 100, null, null, request).getBody().getData().content();
        assertEquals(11, rows.size());
        assertTrue(rows.stream().allMatch(row -> row.countyName().equals("Nairobi")));
        assertTrue(rows.stream().allMatch(row -> row.subCountyId() > 0 && !row.subCountyName().isBlank()));
        assertThrows(MoLSP.go.ke.Social_Assistance_Fund.exceptions.ResourceNotFoundException.class,
                () -> controller.subCounties(999, 0, 100, null, null, request));
        Integer baringoCentralId = jdbc.queryForObject(
                "SELECT id FROM sub_counties WHERE county_id = 30 AND name = 'Baringo Central'", Integer.class);
        var wards = controller.wards(30, baringoCentralId, 0, 100, null, null, request)
                .getBody().getData().content();
        assertEquals(5, wards.size());
        assertTrue(wards.stream().allMatch(ward -> ward.countyName().equals("Baringo")));
        assertTrue(wards.stream().allMatch(ward -> ward.subCountyName().equals("Baringo Central")));
        assertTrue(wards.stream().allMatch(ward -> ward.wardId() > 0 && !ward.wardName().isBlank()));
        assertThrows(MoLSP.go.ke.Social_Assistance_Fund.exceptions.ResourceNotFoundException.class,
                () -> controller.wards(47, baringoCentralId, 0, 100, null, null, request));
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
                        .get("/api/geography/counties/47/sub-counties?pageSize=100")
                        .header("Authorization", bearer)
                        .header("X-Request-ID", "geography-list-test"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("X-Request-ID", "geography-list-test"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.requestId").value("geography-list-test"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.pageNo").value(0))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.content.length()").value(11))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content").doesNotExist())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.content[0].countyName").value("Nairobi"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.content[0].subCountyId").isNumber())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.content[0].subCountyName").isNotEmpty());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/geography/counties/999/sub-counties").header("Authorization", bearer))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        Integer baringoCentralId = jdbc.queryForObject(
                "SELECT id FROM sub_counties WHERE county_id = 30 AND name = 'Baringo Central'", Integer.class);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/geography/counties/30/sub-counties/" + baringoCentralId + "/wards?pageSize=100")
                        .header("Authorization", bearer))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.content.length()").value(5))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.content[0].countyName").value("Baringo"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.content[0].subCountyName").value("Baringo Central"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.content[0].wardName").isNotEmpty())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.content[0].wardId").isNumber());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/geography/counties/47/sub-counties/" + baringoCentralId + "/wards")
                        .header("Authorization", bearer))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void everyMappedEntityInheritsBase() {
        entityManager.getMetamodel().getEntities().stream()
                .filter(entity -> entity.getJavaType() != County.class && entity.getJavaType() != SubCounty.class)
                .forEach(entity ->
                assertTrue(Base.class.isAssignableFrom(entity.getJavaType()),
                        entity.getName() + " must extend Base"));
    }

    @Test
    void applicantCalculatesAgeFromDateOfBirthWhenAvailable() {
        Applicant applicant = new Applicant();
        applicant.setDateOfBirth(java.time.LocalDate.of(2000, 11, 20));
        applicant.setAge(99);

        assertEquals(25, applicant.calculateAge(java.time.LocalDate.of(2026, 11, 19)));
        assertEquals(26, applicant.calculateAge(java.time.LocalDate.of(2026, 11, 20)));
    }

    @Test
    void applicantAgeCanBeMissingOrReportedWithoutDateOfBirth() {
        Applicant applicant = new Applicant();
        assertNull(applicant.calculateAge());
        applicant.setAge(42);
        assertEquals(42, applicant.calculateAge());
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
