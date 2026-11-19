package MoLSP.go.ke.Social_Assistance_Fund.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Gender implements BaseEnum {

    MALE("MALE", "Male", true),
    FEMALE("FEMALE", "Female", true),
    OTHER("OTHER", "Other", true),
    UNDISCLOSED("UNDISCLOSED", "Undisclosed", true);

    private final String code;
    private final String displayName;
    private final boolean active;
}
