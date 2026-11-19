package MoLSP.go.ke.Social_Assistance_Fund.enums;

public enum NotificationChannel {
    SMS,
    EMAIL;

    public String providerValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
