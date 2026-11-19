package MoLSP.go.ke.Social_Assistance_Fund.notifications;

/**
 * Delivery adapter. Implementations must not throw for provider failures; they return a categorised
 * {@link DeliveryResult} and must never log recipients, message bodies or credentials.
 */
public interface NotificationGateway {
    String providerName();

    DeliveryResult send(OutboundNotification notification);
}
