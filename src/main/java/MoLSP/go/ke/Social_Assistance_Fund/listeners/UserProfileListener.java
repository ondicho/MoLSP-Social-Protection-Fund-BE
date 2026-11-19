package MoLSP.go.ke.Social_Assistance_Fund.listeners;

import MoLSP.go.ke.Social_Assistance_Fund.models.User;
import MoLSP.go.ke.Social_Assistance_Fund.utils.PhoneNumberNormalizer;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserProfileListener {
    private final PhoneNumberNormalizer phoneNumbers;

    @PrePersist
    @PreUpdate
    public void prepare(User user) {
        user.setPhoneNumber(phoneNumbers.normalize(user.getPhoneNumber()));
        if (user.getMiddleName() != null && user.getMiddleName().isBlank()) {
            user.setMiddleName(null);
        }
    }
}
