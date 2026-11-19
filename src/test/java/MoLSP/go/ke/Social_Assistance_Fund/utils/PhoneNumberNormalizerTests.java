package MoLSP.go.ke.Social_Assistance_Fund.utils;

import MoLSP.go.ke.Social_Assistance_Fund.exceptions.InvalidPhoneNumberException;
import MoLSP.go.ke.Social_Assistance_Fund.listeners.UserProfileListener;
import MoLSP.go.ke.Social_Assistance_Fund.models.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhoneNumberNormalizerTests {
    private final PhoneNumberNormalizer normalizer = new PhoneNumberNormalizer("KE", "254");

    @Test
    void kenyanPrefixesNormalizeToSameE164Number() {
        for (String input : new String[]{"+254712345678", "254712345678", "0712345678", "0712 345 678",
                "+254 (712) 345-678"}) {
            assertEquals("+254712345678", normalizer.normalize(input));
        }
        assertEquals("+254112345678", normalizer.normalize("0112345678"));
    }

    @Test
    void missingInvalidAndDisallowedNumbersAreRejectedWithoutEchoingInput() {
        for (String input : new String[]{"", "123", "+12025550123", "0712345678 ext 9", "0712345678\n",
                "phone:0712345678", "9".repeat(65)}) {
            var failure = assertThrows(InvalidPhoneNumberException.class, () -> normalizer.normalize(input));
            assertEquals("A valid phone number from an allowed country is required", failure.getMessage());
        }
        assertThrows(InvalidPhoneNumberException.class, () -> normalizer.normalize(null));
    }

    @Test
    void allowedCountriesAndDefaultRegionAreConfigurable() {
        var multi = new PhoneNumberNormalizer("KE", "+254,1");
        assertEquals("+12025550123", multi.normalize("+12025550123"));
        assertThrows(IllegalArgumentException.class, () -> new PhoneNumberNormalizer("XX", "254"));
        assertThrows(IllegalArgumentException.class, () -> new PhoneNumberNormalizer("KE", ""));
        assertThrows(IllegalArgumentException.class, () -> new PhoneNumberNormalizer("KE", "1"));
        assertThrows(IllegalArgumentException.class, () -> new PhoneNumberNormalizer("KE", "254,99999"));
    }

    @Test
    void listenerRequiresPhoneOnInsertAndUpdateAndAllowsMissingMiddleName() {
        var listener = new UserProfileListener(normalizer);
        User user = new User("Jane", null, "Doe");
        assertEquals("Jane Doe", user.getFullName());
        assertNull(user.getMiddleName());
        assertThrows(InvalidPhoneNumberException.class, () -> listener.prepare(user));
        user.setPhoneNumber("0712345678");
        listener.prepare(user);
        assertEquals("+254712345678", user.getPhoneNumber());
        user.setMiddleName(" ");
        listener.prepare(user);
        assertNull(user.getMiddleName());
        user.setPhoneNumber("+12025550123");
        assertThrows(InvalidPhoneNumberException.class, () -> listener.prepare(user));
    }
}
