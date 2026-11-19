package MoLSP.go.ke.Social_Assistance_Fund.models;

import MoLSP.go.ke.Social_Assistance_Fund.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class UserTests {

    @Test
    void constructorGeneratesFullNameInNameOrder() {
        User user = new User(" Jane ", " Mary ", " Doe ");

        assertEquals("Jane Mary Doe", user.getFullName());
        assertEquals(" Jane ", user.getFirstName());
        assertEquals(" Mary ", user.getMiddleName());
        assertEquals(" Doe ", user.getLastName());
    }

    @Test
    void constructorOmitsNullAndBlankNameParts() {
        assertEquals("Jane Doe", new User("Jane", null, "Doe").getFullName());
        assertEquals("Jane Doe", new User("Jane", "  ", "Doe").getFullName());
        assertEquals("", new User(null, null, null).getFullName());
    }

    @Test
    void userDetailsExposeEmailHashAndRoleAuthorities() {
        User user = new User();
        user.setEmail("officer@example.test");
        user.setPasswordHash(new BCryptPasswordEncoder().encode("test-only-password"));
        user.setRole(Role.DATA_ENTRY_OFFICER);

        assertEquals(user.getEmail(), user.getUsername());
        assertTrue(new BCryptPasswordEncoder().matches("test-only-password", user.getPassword()));
        assertTrue(user.isEnabled());
        assertEquals(1, user.getAuthorities().size());
        assertTrue(user.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_DATA_ENTRY_OFFICER")));
    }

    @Test
    void replacingRoleRemovesPreviousAuthority() {
        User user = new User();
        user.setRole(Role.DATA_ENTRY_OFFICER);
        user.setRole(Role.APPROVER);

        assertEquals(1, user.getAuthorities().size());
        assertEquals("ROLE_APPROVER", user.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void missingRoleIsRejected() {
        assertThrows(IllegalStateException.class, () -> new User().getAuthorities());
    }

    @Test
    void serializedUserDoesNotExposePasswordHash() {
        User user = new User();
        user.setPasswordHash("sensitive-password-hash");
        user.setRole(Role.DATA_ENTRY_OFFICER);
        String json = JsonMapper.builder().build().writeValueAsString(user);
        assertFalse(json.contains("sensitive-password-hash"));
        assertFalse(json.contains("\"password\""));
        assertFalse(json.contains("\"passwordHash\""));
    }
}
