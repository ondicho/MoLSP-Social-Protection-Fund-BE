package MoLSP.go.ke.Social_Assistance_Fund.logging;

import MoLSP.go.ke.Social_Assistance_Fund.utils.PiiMasker;
import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class PiiSafeMessageConverter extends ClassicConverter {
    @Override
    public String convert(ILoggingEvent event) {
        return PiiMasker.mask(event.getFormattedMessage());
    }
}
