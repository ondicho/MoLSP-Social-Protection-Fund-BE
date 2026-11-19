package MoLSP.go.ke.Social_Assistance_Fund.models;

import MoLSP.go.ke.Social_Assistance_Fund.enums.BaseEnum;
import MoLSP.go.ke.Social_Assistance_Fund.enums.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoleTests {

    @Test
    void rolesHaveStableCodesAndActiveDisplayNames() {
        for (Role role : Role.values()) {
            assertEquals(role.name(), role.getCode());
            assertFalse(role.getDisplayName().isBlank());
            assertTrue(role.isActive());
            User user = new User();
            user.setRole(role);
            assertEquals("ROLE_" + role.getCode(),
                    user.getAuthorities().iterator().next().getAuthority());
        }
    }

    @Test
    void rolesSortByDisplayName() {
        assertEquals(List.of(Role.APPLICANT, Role.APPROVER, Role.DATA_ENTRY_OFFICER),
                BaseEnum.sortAlphabetically(Role.class));
    }
}
