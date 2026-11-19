package MoLSP.go.ke.Social_Assistance_Fund.notifications;

public record DeliveryResult(boolean delivered, boolean retryable, String errorCategory, String providerReference) {
    public static DeliveryResult delivered(String providerReference) {
        return new DeliveryResult(true, false, null, providerReference);
    }

    public static DeliveryResult retryable(String errorCategory) {
        return new DeliveryResult(false, true, errorCategory, null);
    }

    public static DeliveryResult permanent(String errorCategory) {
        return new DeliveryResult(false, false, errorCategory, null);
    }
}
