package MoLSP.go.ke.Social_Assistance_Fund.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FundCoverage  implements BaseEnum{

    OPEN("OPEN", "Open Fund", true),
    PLWD("PLWD", "PLWD Fund", true),
    WOMEN("WOMEN", "Women Fund", true),
    YOUTH("YOUTH", "Youth Fund", true);

    private final String code;
    private final String displayName;
    private final boolean active;
}
