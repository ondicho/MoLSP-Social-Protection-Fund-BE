package MoLSP.go.ke.Social_Assistance_Fund.notifications;

import MoLSP.go.ke.Social_Assistance_Fund.enums.NotificationChannel;

import java.util.UUID;

public record OutboundNotification(UUID id, NotificationChannel channel, String recipient, String subject,
                                   String title, String body) {
    @Override
    public String toString() {
        return "OutboundNotification[id=" + id + ", channel=" + channel + "]";
    }
}
