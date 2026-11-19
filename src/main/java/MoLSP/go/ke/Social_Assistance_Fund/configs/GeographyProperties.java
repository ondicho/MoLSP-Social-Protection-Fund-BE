package MoLSP.go.ke.Social_Assistance_Fund.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "geography")
public record GeographyProperties(List<County> counties) {
    public record County(Integer code, String name, Integer population2009, Integer population2019,
                         List<SubCounty> subCounties) {
    }

    public record SubCounty(String name, Integer population2009, Integer population2019) {
    }
}
