package MoLSP.go.ke.Social_Assistance_Fund.controllers;

import MoLSP.go.ke.Social_Assistance_Fund.dtos.ResponseWrapper;
import MoLSP.go.ke.Social_Assistance_Fund.exceptions.ResourceNotFoundException;
import MoLSP.go.ke.Social_Assistance_Fund.utils.ResponseHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/geography")
@RequiredArgsConstructor
public class GeographyController {
    private final JdbcTemplate jdbc;
    private final ResponseHandler responses;

    @GetMapping("/counties")
    public ResponseEntity<ResponseWrapper<List<County>>> counties(HttpServletRequest request) {
        List<County> counties = jdbc.query("SELECT id, name FROM counties ORDER BY name",
                (rs, row) -> new County(rs.getInt("id"), rs.getString("name")));
        return responses.success(counties, responses.requestId(request));
    }

    @GetMapping("/counties/{countyId}/sub-counties")
    public ResponseEntity<ResponseWrapper<List<SubCounty>>> subCounties(@PathVariable int countyId,
                                                                        HttpServletRequest request) {
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM counties WHERE id = ?", Integer.class, countyId);
        if (exists == null || exists == 0) {
            throw new ResourceNotFoundException("County", countyId);
        }
        List<SubCounty> subCounties = jdbc.query(
                "SELECT id, county_id, name FROM sub_counties WHERE county_id = ? ORDER BY name",
                (rs, row) -> new SubCounty(rs.getInt("id"), rs.getInt("county_id"), rs.getString("name")),
                countyId);
        return responses.success(subCounties, responses.requestId(request));
    }

    public record County(int id, String name) {
    }

    public record SubCounty(int id, int countyId, String name) {
    }
}
