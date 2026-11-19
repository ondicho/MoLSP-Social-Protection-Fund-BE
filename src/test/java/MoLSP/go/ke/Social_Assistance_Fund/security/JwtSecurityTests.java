package MoLSP.go.ke.Social_Assistance_Fund.security;

import MoLSP.go.ke.Social_Assistance_Fund.configs.SecurityConfig;
import MoLSP.go.ke.Social_Assistance_Fund.enums.Role;
import MoLSP.go.ke.Social_Assistance_Fund.models.User;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

class JwtSecurityTests {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private JwtProvider provider;
    private TestAccounts accounts;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(
                new org.springframework.core.env.MapPropertySource("testCors",
                        java.util.Map.of("app.cors.allowed-origins", "http://localhost:5173")));
        context.register(TestConfiguration.class);
        context.refresh();
        mvc = webAppContextSetup(context)
                .addFilters(new MoLSP.go.ke.Social_Assistance_Fund.filters.CorrelationIdFilter(
                        new MoLSP.go.ke.Social_Assistance_Fund.utils.ResponseHandler()))
                .apply(springSecurity()).build();
        provider = context.getBean(JwtProvider.class);
        accounts = context.getBean(TestAccounts.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void generatedTokenRoundTripsWithUuidSubject() {
        String token = provider.generateAccessToken(accounts.user.getId());
        assertTrue(provider.isValid(token));
        assertEquals(accounts.user.getId().toString(), provider.extractSubject(token));
        assertEquals(900000, provider.getExpirationMs());
    }

    @Test
    void protectedEndpointRequiresBearerAuthentication() throws Exception {
        mvc.perform(get("/test/me").header("X-Request-ID", "security-test-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.status").value(401))
                .andExpect(header().string("X-Request-ID", "security-test-1"))
                .andExpect(jsonPath("$.requestId").value("security-test-1"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void validTokenAuthenticatesWithoutCreatingSession() throws Exception {
        var result = mvc.perform(get("/test/me").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk()).andReturn();
        assertNull(result.getRequest().getSession(false));
    }

    @Test
    void insufficientRoleReturnsForbiddenProblem() throws Exception {
        mvc.perform(get("/test/approve").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.status").value(403));
    }

    @Test
    void approverRoleIsLoadedFromCurrentAccount() throws Exception {
        accounts.user.setRole(Role.APPROVER);
        mvc.perform(get("/test/approve").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void disabledAccountCannotUsePreviouslyIssuedToken() throws Exception {
        String token = bearer();
        accounts.user.setEnabled(false);
        mvc.perform(get("/test/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletedAccountCannotAuthenticate() throws Exception {
        mvc.perform(get("/test/me").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + provider.generateAccessToken(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedTokenReturnsGenericProblemWithoutTokenDetails() throws Exception {
        mvc.perform(get("/test/me").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-sensitive-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("A valid bearer token and active account are required."));
        assertFalse(provider.isValid("invalid-sensitive-token"));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String token = Jwts.builder().subject(accounts.user.getId().toString())
                .expiration(new Date(System.currentTimeMillis() - 60000))
                .signWith(context.getBean(SecretKey.class)).compact();
        assertFalse(provider.isValid(token));
        mvc.perform(get("/test/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        String token = Jwts.builder().subject(accounts.user.getId().toString())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Jwts.SIG.HS256.key().build()).compact();
        mvc.perform(get("/test/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonUuidSubjectIsRejected() throws Exception {
        String token = Jwts.builder().subject("not-a-uuid")
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(context.getBean(SecretKey.class)).compact();
        assertFalse(provider.isValid(token));
        mvc.perform(get("/test/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingSubjectOrExpirationIsRejected() throws Exception {
        SecretKey key = context.getBean(SecretKey.class);
        String missingSubject = Jwts.builder()
                .expiration(new Date(System.currentTimeMillis() + 60000)).signWith(key).compact();
        String missingExpiration = Jwts.builder()
                .subject(accounts.user.getId().toString()).signWith(key).compact();
        for (String token : Set.of(missingSubject, missingExpiration)) {
            assertFalse(provider.isValid(token));
            mvc.perform(get("/test/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void corsAllowsOnlyConfiguredOrigin() throws Exception {
        mvc.perform(options("/test/me").header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
        mvc.perform(options("/test/me").header(HttpHeaders.ORIGIN, "https://untrusted.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void corsConfigurationSupportsPathsAndRejectsWildcards() {
        var config = new SecurityConfig();
        var source = config.corsConfigurationSource("https://frontend.example", "/api/**",
                "GET", "Authorization", "X-Request-ID", 60);
        var matching = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/applications");
        var cors = source.getCorsConfiguration(matching);
        assertNotNull(cors);
        assertEquals(java.util.List.of("https://frontend.example"), cors.getAllowedOrigins());
        assertEquals(java.util.List.of("GET"), cors.getAllowedMethods());
        assertEquals(java.util.List.of("Authorization"), cors.getAllowedHeaders());
        assertEquals(60L, cors.getMaxAge());
        assertNull(source.getCorsConfiguration(
                new org.springframework.mock.web.MockHttpServletRequest("GET", "/outside")));
        assertThrows(IllegalArgumentException.class, () -> config.corsConfigurationSource(
                "*", "/**", "GET", "Authorization", "", 60));
    }

    @Test
    void invalidExpirationAndWeakKeyFailFast() {
        String secret = Base64.getEncoder().encodeToString(context.getBean(SecretKey.class).getEncoded());
        var handler = context.getBean(SecurityErrorHandler.class);
        assertThrows(IllegalArgumentException.class, () -> new JwtProvider(secret, 0, accounts, handler));
        assertThrows(io.jsonwebtoken.security.WeakKeyException.class,
                () -> new JwtProvider(Base64.getEncoder().encodeToString(new byte[8]), 900000, accounts, handler));
    }

    private String bearer() {
        return "Bearer " + provider.generateAccessToken(accounts.user.getId());
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class TestConfiguration {
        @Bean
        SecretKey key() {
            return Jwts.SIG.HS256.key().build();
        }

        @Bean
        TestAccounts accounts() {
            return new TestAccounts();
        }

        @Bean
        SecurityErrorHandler errorHandler() {
            return new SecurityErrorHandler(JsonMapper.builder().build(),
                    new MoLSP.go.ke.Social_Assistance_Fund.utils.ResponseHandler());
        }

        @Bean
        JwtProvider jwtProvider(SecretKey key, TestAccounts accounts, SecurityErrorHandler handler) {
            return new JwtProvider(Base64.getEncoder().encodeToString(key.getEncoded()), 900000, accounts, handler);
        }

        @Bean
        TestController controller() {
            return new TestController();
        }
    }

    static class TestAccounts extends AccountUserDetailsService {
        final User user = new User();

        TestAccounts() {
            super(null);
            user.setId(UUID.randomUUID());
            user.setEmail("officer@example.test");
            user.setRole(Role.DATA_ENTRY_OFFICER);
        }

        @Override
        public User loadUserById(UUID id) {
            if (!user.getId().equals(id)) {
                throw new UsernameNotFoundException("Invalid account");
            }
            return user;
        }
    }

    @RestController
    static class TestController {
        @GetMapping("/test/me")
        public String me() {
            return "authenticated";
        }

        @GetMapping("/test/approve")
        @PreAuthorize("hasRole('APPROVER')")
        public String approve() {
            return "approved";
        }
    }
}
