package MoLSP.go.ke.Social_Assistance_Fund.configs;

import MoLSP.go.ke.Social_Assistance_Fund.security.JwtProvider;
import MoLSP.go.ke.Social_Assistance_Fund.security.SecurityErrorHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    public static final String[] WHITELISTED_URLS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api/auth/login",
            "/api/applications/self-registration",
    };

    public static final String[] BLOCKED_URLS = {
            "/actuator",
            "/actuator/**"
    };
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider,
                                            SecurityErrorHandler errorHandler,
                                            CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
                        .requestMatchers(EndpointRequest.toAnyEndpoint()).denyAll()
                        .requestMatchers(BLOCKED_URLS).denyAll()
                        .requestMatchers(WHITELISTED_URLS).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtProvider, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    FilterRegistrationBean<JwtProvider> jwtFilterRegistration(JwtProvider jwtProvider) {
        var registration = new FilterRegistrationBean<>(jwtProvider);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:}") String allowedOrigins,
            @Value("${app.cors.paths:/**}") String paths,
            @Value("${app.cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}") String methods,
            @Value("${app.cors.allowed-headers:Authorization,Content-Type,X-Request-ID}") String headers,
            @Value("${app.cors.exposed-headers:X-Request-ID}") String exposedHeaders,
            @Value("${app.cors.max-age:3600}") long maxAge) {
        var configuration = new CorsConfiguration();
        List<String> origins = split(allowedOrigins);
        if (origins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalArgumentException("CORS origins must be explicit, without wildcards");
        }
        if (maxAge < 0) {
            throw new IllegalArgumentException("CORS max age must not be negative");
        }
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(split(methods));
        configuration.setAllowedHeaders(split(headers));
        configuration.setExposedHeaders(split(exposedHeaders));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(maxAge);
        var source = new UrlBasedCorsConfigurationSource();
        split(paths).forEach(path -> source.registerCorsConfiguration(path, configuration));
        return source;
    }

    private List<String> split(String value) {
        return Arrays.stream(value.split(",")).map(String::trim)
                .filter(item -> !item.isEmpty()).toList();
    }
}
