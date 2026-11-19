package MoLSP.go.ke.Social_Assistance_Fund.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

class ActuatorSecurityTests {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(AutoConfiguration.class, JwtSecurityTests.TestConfiguration.class)
            .withInitializer(context -> {
                context.getEnvironment().setConversionService(
                        new org.springframework.boot.convert.ApplicationConversionService());
                var properties = assertDoesNotThrow(() ->
                        new org.springframework.core.io.support.ResourcePropertySource("classpath:application.properties"));
                context.getEnvironment().getPropertySources().addLast(properties);
            });

    @Test
    void onlyHealthIsPublicAndHeapDumpIsUnavailableEvenToAuthenticatedUsers() {
        runner.run(context -> {
            org.assertj.core.api.Assertions.assertThat(context).hasNotFailed();
            org.springframework.boot.availability.AvailabilityChangeEvent.publish(
                    context, org.springframework.boot.availability.ReadinessState.ACCEPTING_TRAFFIC);
            org.springframework.boot.availability.AvailabilityChangeEvent.publish(
                    context, org.springframework.boot.availability.LivenessState.CORRECT);
            assertFalse(context.containsBean("heapDumpWebEndpoint"));
            MockMvc mvc = webAppContextSetup(context.getSourceApplicationContext())
                    .apply(springSecurity()).build();
            mvc.perform(get("/actuator/health")).andExpect(status().isOk());
            JwtProvider provider = context.getBean(JwtProvider.class);
            var accounts = context.getBean(JwtSecurityTests.TestAccounts.class);
            String bearer = "Bearer " + provider.generateAccessToken(accounts.user.getId());
            mvc.perform(get("/actuator/heapdump").header("Authorization", bearer))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/actuator/env").header("Authorization", bearer))
                    .andExpect(status().isForbidden());
        });
    }

    @Test
    void corsEnvironmentVariablesReachTheInjectedBean() {
        runner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                new org.springframework.core.env.SystemEnvironmentPropertySource("testEnvironment",
                        java.util.Map.of(
                                "CORS_ALLOWED_ORIGINS", "https://frontend.example",
                                "CORS_PATHS", "/api/**",
                                "CORS_ALLOWED_METHODS", "GET",
                                "CORS_ALLOWED_HEADERS", "Authorization",
                                "CORS_EXPOSED_HEADERS", "X-Request-ID",
                                "CORS_MAX_AGE", "120"
                        )))).run(context -> {
            org.assertj.core.api.Assertions.assertThat(context).hasNotFailed();
            var source = context.getBean("corsConfigurationSource",
                    org.springframework.web.cors.CorsConfigurationSource.class);
            var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/applications");
            var cors = source.getCorsConfiguration(request);
            assertNotNull(cors);
            assertEquals(java.util.List.of("https://frontend.example"), cors.getAllowedOrigins());
            assertEquals(java.util.List.of("GET"), cors.getAllowedMethods());
            assertEquals(java.util.List.of("Authorization"), cors.getAllowedHeaders());
            assertEquals(120L, cors.getMaxAge());
            assertFalse(cors.getAllowCredentials());
        });
    }

    @Test
    void relocatedActuatorEndpointsRemainProtectedWhenExposureIsBroadened() {
        runner.withPropertyValues(
                "management.endpoints.web.base-path=/manage",
                "management.endpoints.web.exposure.include=*",
                "management.endpoint.metrics.access=read-only"
        ).run(context -> {
            org.assertj.core.api.Assertions.assertThat(context).hasNotFailed();
            org.springframework.boot.availability.AvailabilityChangeEvent.publish(
                    context, org.springframework.boot.availability.ReadinessState.ACCEPTING_TRAFFIC);
            org.springframework.boot.availability.AvailabilityChangeEvent.publish(
                    context, org.springframework.boot.availability.LivenessState.CORRECT);
            assertFalse(context.containsBean("heapDumpWebEndpoint"));
            MockMvc mvc = webAppContextSetup(context.getSourceApplicationContext())
                    .apply(springSecurity()).build();
            JwtProvider provider = context.getBean(JwtProvider.class);
            var accounts = context.getBean(JwtSecurityTests.TestAccounts.class);
            String bearer = "Bearer " + provider.generateAccessToken(accounts.user.getId());
            mvc.perform(get("/manage/health")).andExpect(status().isOk());
            mvc.perform(get("/manage/metrics").header("Authorization", bearer))
                    .andExpect(status().isForbidden());
        });
    }

    @Configuration
    @EnableAutoConfiguration(excludeName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
    static class AutoConfiguration {
        @org.springframework.context.annotation.Bean
        org.springframework.boot.health.contributor.HealthIndicator dbHealthIndicator() {
            return () -> org.springframework.boot.health.contributor.Health.up().build();
        }
    }
}
