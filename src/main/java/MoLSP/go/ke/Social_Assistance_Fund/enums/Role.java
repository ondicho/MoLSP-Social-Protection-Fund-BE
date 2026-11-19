package MoLSP.go.ke.Social_Assistance_Fund.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Role implements BaseEnum {

    DATA_ENTRY_OFFICER("DATA_ENTRY_OFFICER", "Data Entry Officer", true),
    APPROVER("APPROVER", "Approver", true),
    APPLICANT("APPLICANT", "Applicant", true);

    private final String code;
    private final String displayName;
    private final boolean active;
}
