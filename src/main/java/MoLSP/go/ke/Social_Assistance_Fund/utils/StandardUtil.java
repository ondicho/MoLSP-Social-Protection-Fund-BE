
package MoLSP.go.ke.Social_Assistance_Fund.utils;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;

@Service
public class StandardUtil {

/**
 * Feature	Benefit
 * Null handling	Prevents NullPointerException
 * Blank handling	Returns an empty string for blank input
 * Unicode normalization	Converts accented characters into their ASCII equivalents
 * Locale.ROOT	Ensures consistent lowercase conversion regardless of server locale
 * Special characters	Replaces consecutive special characters with a single hyphen
 * Hyphen cleanup	Removes leading and trailing hyphens
 */
    public String generateSlug(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        return Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
