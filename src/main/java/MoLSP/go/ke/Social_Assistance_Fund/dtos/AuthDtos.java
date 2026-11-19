package MoLSP.go.ke.Social_Assistance_Fund.dtos;

import MoLSP.go.ke.Social_Assistance_Fund.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 72) String password) {
    }

    public record LoginResponse(
            String accessToken,
            String tokenType,
            long expiresInMs,
            UserSummary user) {
    }

    public record UserSummary(UUID id, String firstName, String lastName, String email, Role role) {
    }
}
