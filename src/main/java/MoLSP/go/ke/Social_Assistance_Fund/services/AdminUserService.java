package MoLSP.go.ke.Social_Assistance_Fund.services;

import MoLSP.go.ke.Social_Assistance_Fund.dtos.AdminUserDtos;
import MoLSP.go.ke.Social_Assistance_Fund.dtos.PaginatedResponse;
import MoLSP.go.ke.Social_Assistance_Fund.dtos.ResponseWrapper;
import MoLSP.go.ke.Social_Assistance_Fund.enums.Role;
import MoLSP.go.ke.Social_Assistance_Fund.exceptions.DuplicateResourceException;
import MoLSP.go.ke.Social_Assistance_Fund.exceptions.ResourceNotFoundException;
import MoLSP.go.ke.Social_Assistance_Fund.models.User;
import MoLSP.go.ke.Social_Assistance_Fund.repositories.UserRepository;
import MoLSP.go.ke.Social_Assistance_Fund.utils.PaginationUtil;
import MoLSP.go.ke.Social_Assistance_Fund.utils.ResponseHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.EntityManager;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final PaginationUtil pagination;
    private final ResponseHandler responses;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public ResponseEntity<ResponseWrapper<PaginatedResponse<AdminUserDtos.UserResponse>>> list(
            Integer pageNo, Integer pageSize, String sortBy, String sortDirection,
            String search, Role role, Boolean enabled, String requestId) {
        validateSort(sortBy);
        Pageable pageable = pagination.buildPageable(pageNo, pageSize, sortBy, sortDirection, "createdAt");
        Page<AdminUserDtos.UserResponse> page = users.findAll(specification(search, role, enabled), pageable)
                .map(this::toResponse);
        return responses.paginated(page, "Users retrieved", requestId);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ResponseWrapper<AdminUserDtos.UserResponse>> get(UUID id, String requestId) {
        return responses.success(toResponse(find(id)), "User retrieved", requestId);
    }

    @Transactional
    public ResponseEntity<ResponseWrapper<AdminUserDtos.UserResponse>> create(
            AdminUserDtos.CreateRequest request, String requestId) {
        ensureManagedRole(request.role());
        String email = normalizeEmail(request.email());
        if (users.findByEmail(email).isPresent()) {
            throw new DuplicateResourceException("User email already exists");
        }
        String firstName = request.firstName().strip();
        String middleName = request.middleName() != null ? request.middleName().strip() : null;
        String lastName = request.lastName().strip();
        validateFullName(firstName, middleName, lastName);
        User user = new User(firstName, middleName, lastName);
        user.setEmail(email);
        user.setPhoneNumber(request.phoneNumber());
        user.setRole(request.role());
        ensurePasswordLength(request.temporaryPassword());
        user.setPasswordHash(passwords.encode(request.temporaryPassword()));
        return responses.created(toResponse(users.save(user)), "User created", requestId);
    }

    @Transactional
    public ResponseEntity<ResponseWrapper<AdminUserDtos.UserResponse>> update(
            UUID id, AdminUserDtos.UpdateRequest request, String requestId) {
        User user = find(id);
        ensureManagedRole(request.role());
        ensureVersion(user, request.version());
        ensureUniqueEmail(request.email(), id);
        ensureEnabledAdministratorRemains(user, request.role(), user.isEnabled());

        String firstName = request.firstName().strip();
        String middleName = request.middleName() != null ? request.middleName().strip() : null;
        String lastName = request.lastName().strip();
        validateFullName(firstName, middleName, lastName);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setMiddleName(middleName);
        user.setFullName(firstName + " " + (middleName != null ? middleName + " " : "") + lastName);
        user.setEmail(normalizeEmail(request.email()));
        user.setPhoneNumber(request.phoneNumber());
        user.setRole(request.role());
        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            ensurePasswordLength(request.newPassword());
            user.setPasswordHash(passwords.encode(request.newPassword()));
        }
        users.save(user);
        entityManager.flush();
        return responses.success(toResponse(user), "User updated", requestId);
    }

    @Transactional
    public ResponseEntity<ResponseWrapper<AdminUserDtos.UserResponse>> setEnabled(
            UUID id, boolean enabled, String requestId) {
        User user = find(id);
        if (!enabled) {
            ensureEnabledAdministratorRemains(user, user.getRole(), false);
        }
        user.setEnabled(enabled);
        users.save(user);
        entityManager.flush();
        return responses.success(toResponse(user),
                enabled ? "User enabled" : "User deactivated", requestId);
    }

    private Specification<User> specification(String search, Role role, Boolean enabled) {
        Specification<User> spec = (root, query, cb) -> cb.conjunction();
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.strip().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("firstName")), pattern),
                    cb.like(cb.lower(root.get("lastName")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern)));
        }
        if (role != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("role"), role));
        }
        if (enabled != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("enabled"), enabled));
        }
        return spec;
    }

    private void ensureManagedRole(Role role) {
        if (role == null || role == Role.APPLICANT || !role.isActive()) {
            throw new IllegalArgumentException("Only platform admins, approvers, and data-entry officers can be managed here");
        }
    }

    private void ensureVersion(User user, long expectedVersion) {
        if (user.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(User.class, user.getId());
        }
    }

    private void ensureUniqueEmail(String email, UUID id) {
        users.findByEmail(normalizeEmail(email)).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new DuplicateResourceException("User email already exists");
            }
        });
    }

    private void ensureEnabledAdministratorRemains(User user, Role nextRole, boolean nextEnabled) {
        if (user.getRole() == Role.PLATFORM_ADMIN && user.isEnabled()
                && (nextRole != Role.PLATFORM_ADMIN || !nextEnabled)
                && users.findEnabledByRoleForUpdate(Role.PLATFORM_ADMIN).size() <= 1) {
            throw new IllegalArgumentException("The last enabled platform administrator cannot be deactivated or demoted");
        }
    }

    private User find(UUID id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private AdminUserDtos.UserResponse toResponse(User user) {
        return new AdminUserDtos.UserResponse(user.getId(), user.getFirstName(), user.getMiddleName(), user.getLastName(),
                user.getEmail(), user.getPhoneNumber(), user.getRole(), user.isEnabled(),
                user.getVersion(), user.getCreatedAt(), user.getUpdatedAt());
    }

    private void validateSort(String sortBy) {
        if (sortBy != null && !sortBy.isBlank()
                && !java.util.Set.of("createdAt", "updatedAt", "email", "firstName", "middleName", "lastName", "role", "enabled")
                .contains(sortBy)) {
            throw new IllegalArgumentException("Unsupported user sort field");
        }
    }

    private void validateFullName(String firstName, String middleName, String lastName) {
        int length = firstName.length() + lastName.length() + 1;
        if (middleName != null) {
            length += middleName.length() + 1;
        }
        if (length > 200) {
            throw new IllegalArgumentException("Combined first, middle, and last name must not exceed 200 characters");
        }
    }

    private void ensurePasswordLength(String password) {
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes");
        }
    }
}
