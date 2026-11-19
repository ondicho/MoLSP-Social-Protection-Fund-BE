package MoLSP.go.ke.Social_Assistance_Fund.utils;

import java.util.regex.Pattern;

public final class PiiMasker {
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+\\S+");
    private static final Pattern SECRETS = Pattern.compile(
            "(?i)([\"']?(?:password(?:_?hash)?|token|access[_-]?token|refresh[_-]?token|authorization|secret)"
                    + "[\"']?\\s*[:=]\\s*)(?:\"[^\"]*\"|'[^']*'|[^\\s,;}]+)");
    private static final Pattern IDENTIFIERS = Pattern.compile(
            "(?i)([\"']?(?:id(?:[_ -]?(?:number|card))?|passport(?:[_ -]?number)?|"
                    + "identity[_ -]?(?:number|card)|phone(?:[_ -]?number)?|email|"
                    + "(?:first|middle|last|full)[_ -]?name|address)[\"']?\\s*[:=]\\s*)"
                    + "(?:\"[^\"]*\"|'[^']*'|[^\\s,;}]+)");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
    private static final Pattern LONG_NUMBER = Pattern.compile("(?<![A-Za-z0-9])\\+?\\d(?:[ ()-]*\\d){6,}(?![A-Za-z0-9])");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b");

    private PiiMasker() {
    }

    public static String mask(String message) {
        if (message == null) {
            return "";
        }
        String masked = BEARER.matcher(message).replaceAll("Bearer [REDACTED]");
        masked = SECRETS.matcher(masked).replaceAll("$1[REDACTED]");
        masked = IDENTIFIERS.matcher(masked).replaceAll("$1[REDACTED]");
        masked = EMAIL.matcher(masked).replaceAll("[REDACTED]");
        masked = LONG_NUMBER.matcher(masked).replaceAll("[REDACTED]");
        return JWT.matcher(masked).replaceAll("[REDACTED]").replace('\r', ' ').replace('\n', ' ');
    }
}
