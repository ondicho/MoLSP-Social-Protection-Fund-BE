package MoLSP.go.ke.Social_Assistance_Fund.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FundType  implements BaseEnum{

    NATIONAL("NATIONAL", "National Fund", true),
    COUNTY("COUNTY", "County Fund", true);

    private final String code;
    private final String displayName;
    private final boolean active;
}
