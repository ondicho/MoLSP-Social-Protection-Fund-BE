package MoLSP.go.ke.Social_Assistance_Fund.notifications;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StubNotificationGateway implements NotificationGateway {
    @Override
    public String providerName() {
        return "stub";
    }

    @Override
    public DeliveryResult send(OutboundNotification notification) {
        log.info("event=notification_stub_accepted notificationId={} channel={}",
                notification.id(), notification.channel());
        return DeliveryResult.delivered("stub-" + notification.id());
    }
}
