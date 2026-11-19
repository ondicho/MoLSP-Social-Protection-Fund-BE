package MoLSP.go.ke.Social_Assistance_Fund.utils;

import java.util.function.Supplier;

public final class PublicRegistrationAuditContext {
    public static final String ACTOR = "PUBLIC_SELF_REGISTRATION";
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();

    private PublicRegistrationAuditContext() {
    }

    public static boolean isActive() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    public static <T> T execute(Supplier<T> operation) {
        ACTIVE.set(true);
        try {
            return operation.get();
        } finally {
            ACTIVE.remove();
        }
    }
}
