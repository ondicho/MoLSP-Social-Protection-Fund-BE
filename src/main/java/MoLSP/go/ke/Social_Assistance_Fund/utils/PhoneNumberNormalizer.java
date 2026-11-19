package MoLSP.go.ke.Social_Assistance_Fund.utils;

import MoLSP.go.ke.Social_Assistance_Fund.exceptions.InvalidPhoneNumberException;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PhoneNumberNormalizer {
    private final PhoneNumberUtil phones = PhoneNumberUtil.getInstance();
    private final String defaultRegion;
    private final Set<Integer> allowedCodes;

    public PhoneNumberNormalizer(@Value("${app.phone.default-region:KE}") String defaultRegion,
                                 @Value("${app.phone.allowed-country-codes:254}") String allowedCodes) {
        this.defaultRegion = defaultRegion.strip().toUpperCase(Locale.ROOT);
        if (!phones.getSupportedRegions().contains(this.defaultRegion)) {
            throw new IllegalArgumentException("Invalid phone default region configuration");
        }
        try {
            this.allowedCodes = Arrays.stream(allowedCodes.split(",", -1)).map(String::strip)
                    .map(code -> code.startsWith("+") ? code.substring(1) : code)
                    .map(Integer::valueOf).collect(Collectors.toUnmodifiableSet());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid allowed phone country codes configuration");
        }
        if (this.allowedCodes.isEmpty() || !phones.getSupportedCallingCodes().containsAll(this.allowedCodes)
                || !this.allowedCodes.contains(phones.getCountryCodeForRegion(this.defaultRegion))) {
            throw new IllegalArgumentException("Phone country codes must be supported and include the default region");
        }
    }

    public String normalize(String input) {
        if (input == null || input.isBlank() || input.length() > 64
                || !input.matches("\\+?[0-9 ()-]+")) {
            throw new InvalidPhoneNumberException();
        }
        String compact = input.replaceAll("[ ()-]", "");
        try {
            var number = phones.parse(compact, defaultRegion);
            if (number.hasExtension() || !phones.isValidNumber(number)
                    || !allowedCodes.contains(number.getCountryCode())) {
                throw new InvalidPhoneNumberException();
            }
            return phones.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException exception) {
            throw new InvalidPhoneNumberException();
        }
    }
}
