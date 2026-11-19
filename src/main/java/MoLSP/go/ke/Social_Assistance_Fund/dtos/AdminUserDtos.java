package MoLSP.go.ke.Social_Assistance_Fund.dtos;

import MoLSP.go.ke.Social_Assistance_Fund.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AdminUserDtos {
    private AdminUserDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 200) String firstName,
            @Size(max = 200) String middleName,
            @NotBlank @Size(max = 200) String lastName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 16) String phoneNumber,
            @NotNull Role role,
            @NotBlank @Size(min = 12, max = 72) String temporaryPassword) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 200) String firstName,
            @Size(max = 200) String middleName,
            @NotBlank @Size(max = 200) String lastName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 16) String phoneNumber,
            @NotNull Role role,
            @NotNull Long version,
            @Size(min = 12, max = 72) String newPassword) {
    }

    public record UserResponse(
            UUID id,
            String firstName,
            String middleName,
            String lastName,
            String email,
            String phoneNumber,
            Role role,
            boolean enabled,
            long version,
            Instant createdAt,
            Instant updatedAt) {
    }
}
