package MoLSP.go.ke.Social_Assistance_Fund.enums;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApplicationStatus  implements BaseEnum {
    DRAFT("Draft", "Draft", true),
    SUBMITTED("Submitted", "Submitted", true),
    UNDER_REVIEW("Under Review", "Under Review", true),
    APPROVED("Approved", "Approved", true),
    REJECTED("Rejected", "Rejected", true),
    RETURNED_FOR_CORRECTION("Returned for Correction", "Returned for Correction", true);

    private final String code;
    private final String displayName;
    private final boolean active;
}
