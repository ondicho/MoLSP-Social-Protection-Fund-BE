package MoLSP.go.ke.Social_Assistance_Fund.controllers;

import MoLSP.go.ke.Social_Assistance_Fund.dtos.GeographyDtos;
import MoLSP.go.ke.Social_Assistance_Fund.dtos.PaginatedResponse;
import MoLSP.go.ke.Social_Assistance_Fund.dtos.ResponseWrapper;
import MoLSP.go.ke.Social_Assistance_Fund.services.GeographyService;
import MoLSP.go.ke.Social_Assistance_Fund.utils.ResponseHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/geography")
@RequiredArgsConstructor
public class GeographyController {
    private final GeographyService geographyService;
    private final ResponseHandler responses;

    @GetMapping("/counties")
    public ResponseEntity<ResponseWrapper<PaginatedResponse<GeographyDtos.County>>> counties(
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection,
            HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return geographyService.counties(pageNo, pageSize, sortBy, sortDirection, requestId);
    }

    @GetMapping("/counties/{countyId}/sub-counties")
    public ResponseEntity<ResponseWrapper<PaginatedResponse<GeographyDtos.SubCounty>>> subCounties(
            @PathVariable int countyId,
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection,
            HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return geographyService.subCounties(countyId, pageNo, pageSize, sortBy, sortDirection,
                requestId);
    }

    @GetMapping("/counties/{countyId}/sub-counties/{subCountyId}/wards")
    public ResponseEntity<ResponseWrapper<PaginatedResponse<GeographyDtos.Ward>>> wards(
            @PathVariable int countyId,
            @PathVariable int subCountyId,
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection,
            HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return geographyService.wards(countyId, subCountyId, pageNo, pageSize, sortBy, sortDirection,
                requestId);
    }
}
