package MoLSP.go.ke.Social_Assistance_Fund.models;

import MoLSP.go.ke.Social_Assistance_Fund.enums.Role;
import MoLSP.go.ke.Social_Assistance_Fund.listeners.UserProfileListener;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Set;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity
@EntityListeners(UserProfileListener.class)
@Table(name = "users")
@Getter
@Setter
public class User extends Base implements UserDetails {

    public User() {
    }

    public User(String firstName, String middleName, String lastName) {
        this.firstName = firstName;
        this.middleName = middleName;
        this.lastName = lastName;
        this.fullName = Stream.of(firstName, middleName, lastName)
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.joining(" "));
    }

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "phone_number", length = 16, unique = true)
    private String phoneNumber;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "first_name", nullable = false, length = 200)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 200)
    private String lastName;

    @Column(name = "middle_name", length = 200)
    private String middleName;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_code", nullable = false, length = 50)
    private Role role;

    @Override
    public Set<SimpleGrantedAuthority> getAuthorities() {
        if (role == null || !role.isActive()) {
            throw new IllegalStateException("User must have a defined role");
        }
        return Set.of(new SimpleGrantedAuthority("ROLE_" + role.getCode()));
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

}
