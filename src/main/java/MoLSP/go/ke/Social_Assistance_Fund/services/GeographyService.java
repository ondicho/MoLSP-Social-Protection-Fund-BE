package MoLSP.go.ke.Social_Assistance_Fund.services;

import MoLSP.go.ke.Social_Assistance_Fund.dtos.GeographyDtos;
import MoLSP.go.ke.Social_Assistance_Fund.dtos.PaginatedResponse;
import MoLSP.go.ke.Social_Assistance_Fund.dtos.ResponseWrapper;
import MoLSP.go.ke.Social_Assistance_Fund.exceptions.ResourceNotFoundException;
import MoLSP.go.ke.Social_Assistance_Fund.utils.PaginationUtil;
import MoLSP.go.ke.Social_Assistance_Fund.utils.ResponseHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GeographyService {
    private final JdbcTemplate jdbc;
    private final PaginationUtil pagination;
    private final ResponseHandler responses;

    public ResponseEntity<ResponseWrapper<PaginatedResponse<GeographyDtos.County>>> counties(
            Integer pageNo, Integer pageSize, String sortBy, String sortDirection, String requestId) {
        Pageable pageable = pagination.buildPageable(pageNo, pageSize, sortBy, sortDirection, "name");
        Page<GeographyDtos.County> page = lookupPage("counties c", "c.id AS id, c.name AS name", "",
                (rs, row) -> new GeographyDtos.County(rs.getInt("id"), rs.getString("name")),
                pageable, "name", "id");
        return responses.paginated(page, "Counties retrieved", requestId);
    }

    public ResponseEntity<ResponseWrapper<PaginatedResponse<GeographyDtos.SubCounty>>> subCounties(
            int countyId, Integer pageNo, Integer pageSize, String sortBy, String sortDirection, String requestId) {
        requireCounty(countyId);
        Pageable pageable = pagination.buildPageable(pageNo, pageSize, sortBy, sortDirection, "name");
        Page<GeographyDtos.SubCounty> page = lookupPage(
                "sub_counties sc JOIN counties c ON c.id = sc.county_id",
                "sc.id AS subCountyId, sc.name AS subCountyName, c.name AS countyName",
                " WHERE sc.county_id = ?",
                (rs, row) -> new GeographyDtos.SubCounty(
                        rs.getInt("subCountyId"), rs.getString("subCountyName"), rs.getString("countyName")),
                pageable, "subCountyName", "subCountyId", countyId);
        return responses.paginated(page, "Sub-counties retrieved", requestId);
    }

    public ResponseEntity<ResponseWrapper<PaginatedResponse<GeographyDtos.Ward>>> wards(
            int countyId, int subCountyId, Integer pageNo, Integer pageSize,
            String sortBy, String sortDirection, String requestId) {
        requireCountySubCounty(countyId, subCountyId);
        Pageable pageable = pagination.buildPageable(pageNo, pageSize, sortBy, sortDirection, "name");
        Page<GeographyDtos.Ward> page = lookupPage(
                "wards w JOIN sub_counties sc ON sc.id = w.sub_county_id "
                        + "JOIN counties c ON c.id = sc.county_id",
                "c.name AS countyName, sc.name AS subCountyName, w.name AS wardName, w.id AS wardId",
                " WHERE sc.id = ? AND c.id = ?",
                (rs, row) -> new GeographyDtos.Ward(
                        rs.getString("countyName"), rs.getString("subCountyName"),
                        rs.getString("wardName"), rs.getInt("wardId")),
                pageable, "wardName", "wardId", subCountyId, countyId);
        return responses.paginated(page, "Wards retrieved", requestId);
    }

    private void requireCounty(int countyId) {
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM counties WHERE id = ?", Integer.class, countyId);
        if (exists == null || exists == 0) {
            throw new ResourceNotFoundException("County", countyId);
        }
    }

    private void requireCountySubCounty(int countyId, int subCountyId) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sub_counties WHERE id = ? AND county_id = ?",
                Integer.class, subCountyId, countyId);
        if (exists == null || exists == 0) {
            throw new ResourceNotFoundException("Sub-county", subCountyId);
        }
    }

    private <T> Page<T> lookupPage(String fromClause, String columns, String where, RowMapper<T> mapper,
                                   Pageable pageable, String nameColumn, String idColumn,
                                   Object... queryParameters) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + fromClause + where, Long.class, queryParameters);
        String sortColumn = sortColumn(pageable, nameColumn, idColumn);
        String direction = pageable.getSort().stream().findFirst()
                .map(order -> order.isAscending() ? "ASC" : "DESC")
                .orElse("ASC");
        Object[] pageParameters = Arrays.copyOf(queryParameters, queryParameters.length + 2);
        pageParameters[queryParameters.length] = pageable.getPageSize();
        pageParameters[queryParameters.length + 1] = pageable.getOffset();
        List<T> content = jdbc.query("SELECT " + columns + " FROM " + fromClause + where
                        + " ORDER BY " + sortColumn + " " + direction + " LIMIT ? OFFSET ?",
                mapper, pageParameters);
        return new PageImpl<>(content, pageable, total);
    }

    private String sortColumn(Pageable pageable, String nameColumn, String idColumn) {
        String requested = pageable.getSort().stream().findFirst()
                .map(order -> order.getProperty())
                .orElse(PaginationUtil.DEFAULT_SORT_BY);
        return switch (requested) {
            case "name", "createdAt" -> nameColumn;
            case "id" -> idColumn;
            default -> throw new IllegalArgumentException("sortBy must be 'name' or 'id' for geography lookups");
        };
    }
}
