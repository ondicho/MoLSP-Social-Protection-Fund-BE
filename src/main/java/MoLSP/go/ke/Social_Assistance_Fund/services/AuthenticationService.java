package MoLSP.go.ke.Social_Assistance_Fund.services;

import MoLSP.go.ke.Social_Assistance_Fund.dtos.AuthDtos;
import MoLSP.go.ke.Social_Assistance_Fund.models.User;
import MoLSP.go.ke.Social_Assistance_Fund.repositories.UserRepository;
import MoLSP.go.ke.Social_Assistance_Fund.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtProvider jwtProvider;

    @Transactional(readOnly = true)
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new BadCredentialsException("Invalid email or password");
        }
        User user = users.findByEmail(request.email().strip().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!user.isEnabled() || !passwords.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return new AuthDtos.LoginResponse(jwtProvider.generateAccessToken(user.getId()), "Bearer",
                jwtProvider.getExpirationMs(),
                new AuthDtos.UserSummary(user.getId(), user.getFirstName(), user.getLastName(),
                        user.getEmail(), user.getRole()));
    }
}
