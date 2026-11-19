package MoLSP.go.ke.Social_Assistance_Fund.utils;

import MoLSP.go.ke.Social_Assistance_Fund.configs.GeographyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Component
@EnableConfigurationProperties(GeographyProperties.class)
@RequiredArgsConstructor
@Slf4j
public class GeographySeeder implements ApplicationRunner {
    private final GeographyProperties geography;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    @Override
    public void run(ApplicationArguments args) {
        validate();
        transactions.executeWithoutResult(status -> {
            for (var county : geography.counties()) {
                var matching = jdbc.query("SELECT id, name FROM counties WHERE id = ? OR name = ? FOR UPDATE",
                        (rs, row) -> new ExistingCounty(rs.getInt("id"), rs.getString("name")),
                        county.code(), county.name());
                if (matching.stream().anyMatch(existing ->
                        existing.id() != county.code() || !existing.name().equals(county.name()))) {
                    throw new IllegalStateException("County seed conflicts with existing county identity");
                }
                jdbc.update("""
                        INSERT INTO counties (id, name, population_2009, population_2019)
                        VALUES (?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE population_2009 = ?, population_2019 = ?
                        """, county.code(), county.name(), county.population2009(), county.population2019(),
                        county.population2009(), county.population2019());
                for (var sub : county.subCounties()) {
                    jdbc.update("""
                            INSERT INTO sub_counties (county_id, name, population_2009, population_2019)
                            VALUES (?, ?, ?, ?)
                            ON DUPLICATE KEY UPDATE population_2009 = ?, population_2019 = ?
                            """, county.code(), sub.name(), sub.population2009(), sub.population2019(),
                            sub.population2009(), sub.population2019());
                }
            }
        });
        log.info("event=geography_seed_completed counties={} subcounties={}", geography.counties().size(),
                geography.counties().stream().mapToInt(county -> county.subCounties().size()).sum());
    }

    private void validate() {
        if (geography.counties() == null || geography.counties().size() != 47) {
            throw new IllegalStateException("Geography seed must contain exactly 47 counties");
        }
        Set<Integer> codes = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (var county : geography.counties()) {
            if (county == null || county.code() == null || county.code() < 1 || county.code() > 47
                    || !codes.add(county.code()) || !validName(county.name(), 100)
                    || !names.add(key(county.name())) || !validPopulation(county.population2009())
                    || !validPopulation(county.population2019())
                    || county.subCounties() == null || county.subCounties().isEmpty()) {
                throw new IllegalStateException("Invalid or duplicate county in geography seed");
            }
            Set<String> subNames = new HashSet<>();
            for (var sub : county.subCounties()) {
                if (sub == null || !validName(sub.name(), 150) || !subNames.add(key(sub.name()))
                        || !validPopulation(sub.population2009()) || !validPopulation(sub.population2019())) {
                    throw new IllegalStateException("Invalid or duplicate sub-county in geography seed");
                }
            }
        }
    }

    private boolean validName(String name, int maximum) {
        return name != null && !name.isBlank() && name.equals(name.strip()) && name.length() <= maximum;
    }

    private boolean validPopulation(Integer population) {
        return population != null && population >= 0;
    }

    private String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private record ExistingCounty(int id, String name) {
    }
}
