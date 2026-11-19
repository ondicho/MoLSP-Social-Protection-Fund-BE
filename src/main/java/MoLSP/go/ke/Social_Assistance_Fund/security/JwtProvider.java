package MoLSP.go.ke.Social_Assistance_Fund.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtProvider extends OncePerRequestFilter {

    private final SecretKey key;
    private final long expirationMs;
    private final AccountUserDetailsService userDetailsService;
    private final SecurityErrorHandler errorHandler;

    public JwtProvider(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.access-expiration-ms:900000}") long expirationMs,
                       AccountUserDetailsService userDetailsService,
                       SecurityErrorHandler errorHandler) {
        if (expirationMs <= 0) {
            throw new IllegalArgumentException("JWT access expiration must be positive");
        }
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        this.expirationMs = expirationMs;
        this.userDetailsService = userDetailsService;
        this.errorHandler = errorHandler;
    }

    public String generateAccessToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public String extractSubject(String token) {
        return parse(token).getSubject();
    }

    public boolean isValid(String token) {
        try {
            UUID.fromString(extractSubject(token));
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            log.warn("JWT validation rejected");
            return false;
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            UserDetails user;
            try {
                UUID userId = UUID.fromString(extractSubject(header.substring(7)));
                user = userDetailsService.loadUserById(userId);
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException exception) {
                reject(request, response);
                return;
            }
            if (!user.isEnabled() || !user.isAccountNonLocked()
                    || !user.isAccountNonExpired() || !user.isCredentialsNonExpired()) {
                reject(request, response);
                return;
            }
            var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        log.warn("Bearer authentication rejected");
        errorHandler.unauthorized(request, response);
    }

    private Claims parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        if (claims.getSubject() == null || claims.getSubject().isBlank() || claims.getExpiration() == null) {
            throw new MalformedJwtException("Required access token claims are missing");
        }
        return claims;
    }
}
