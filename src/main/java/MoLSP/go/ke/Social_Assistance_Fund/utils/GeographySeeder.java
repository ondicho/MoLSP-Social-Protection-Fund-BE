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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
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
        SeedSummary summary = transactions.execute(status -> seed());
        if (summary == null) {
            throw new IllegalStateException("Geography seed transaction returned no result");
        }
        log.info("event=geography_seed_completed counties={} subcounties={} wards={} "
                        + "matched_subcounty_groups={} unmatched_groups={} unmatched_group_wards={}",
                geography.counties().size(),
                geography.counties().stream().mapToInt(county -> county.subCounties().size()).sum(),
                summary.eligibleWards(), summary.matchedGroups(), summary.unmatchedGroups(),
                summary.unmatchedWards());
    }

    private SeedSummary seed() {
        int matchedGroups = 0;
        int unmatchedGroups = 0;
        int eligibleWards = 0;
        int unmatchedWards = 0;
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

            Map<String, Integer> subCountyIdsByExactName = jdbc.query(
                    "SELECT id, name FROM sub_counties WHERE county_id = ?", rs -> {
                        Map<String, Integer> ids = new HashMap<>();
                        while (rs.next()) {
                            ids.put(rs.getString("name"), rs.getInt("id"));
                        }
                        return ids;
                    }, county.code());
            for (var group : county.constituencies()) {
                Integer subCountyId = subCountyIdsByExactName.get(group.name());
                if (subCountyId == null) {
                    unmatchedGroups++;
                    unmatchedWards += group.wards().size();
                    continue;
                }
                matchedGroups++;
                for (String ward : group.wards()) {
                    jdbc.update("""
                            INSERT INTO wards (sub_county_id, name) VALUES (?, ?)
                            ON DUPLICATE KEY UPDATE name = VALUES(name)
                            """, subCountyId, ward);
                    eligibleWards++;
                }
            }
        }
        return new SeedSummary(matchedGroups, unmatchedGroups, eligibleWards, unmatchedWards);
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
                    || county.subCounties() == null || county.subCounties().isEmpty()
                    || county.constituencies() == null) {
                throw new IllegalStateException("Invalid or duplicate county in geography seed");
            }
            Set<String> subNames = new HashSet<>();
            for (var sub : county.subCounties()) {
                if (sub == null || !validName(sub.name(), 150) || !subNames.add(key(sub.name()))
                        || !validPopulation(sub.population2009()) || !validPopulation(sub.population2019())) {
                    throw new IllegalStateException("Invalid or duplicate sub-county in geography seed");
                }
            }
            Set<Integer> sourceIds = new HashSet<>();
            Set<String> constituencyNames = new HashSet<>();
            for (var group : county.constituencies()) {
                if (group == null || group.subcountyId() == null || group.subcountyId() < 1
                        || !sourceIds.add(group.subcountyId()) || !validName(group.name(), 150)
                        || !constituencyNames.add(key(group.name())) || group.wards() == null) {
                    throw new IllegalStateException("Invalid or duplicate constituency group in geography seed");
                }
                Set<String> wardNames = new HashSet<>();
                for (String ward : group.wards()) {
                    if (!validName(ward, 150) || !wardNames.add(key(ward))) {
                        throw new IllegalStateException("Invalid or duplicate ward in geography seed");
                    }
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

    private record SeedSummary(int matchedGroups, int unmatchedGroups, int eligibleWards, int unmatchedWards) {
    }
}
