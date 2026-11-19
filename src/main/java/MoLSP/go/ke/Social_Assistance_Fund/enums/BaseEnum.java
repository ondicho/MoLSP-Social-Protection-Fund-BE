package MoLSP.go.ke.Social_Assistance_Fund.enums;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public interface BaseEnum {
    String getCode();

    String getDisplayName();

    boolean isActive();

    static <E extends Enum<E> & BaseEnum> List<E> sortAlphabetically(Class<E> enumClass) {
        return Arrays.stream(enumClass.getEnumConstants())
                .sorted(Comparator.comparing(BaseEnum::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
