package MoLSP.go.ke.Social_Assistance_Fund.controllers;

import MoLSP.go.ke.Social_Assistance_Fund.dtos.PaginatedResponse;
import MoLSP.go.ke.Social_Assistance_Fund.dtos.ResponseWrapper;
import MoLSP.go.ke.Social_Assistance_Fund.dtos.SocialFundDtos;
import MoLSP.go.ke.Social_Assistance_Fund.enums.FundType;
import MoLSP.go.ke.Social_Assistance_Fund.services.SocialFundService;
import MoLSP.go.ke.Social_Assistance_Fund.utils.ResponseHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/social-funds")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class SocialFundController {
    private final SocialFundService service;
    private final ResponseHandler responses;

    @GetMapping
    public ResponseEntity<ResponseWrapper<PaginatedResponse<SocialFundDtos.FundResponse>>> list(
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) FundType type,
            HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return service.list(pageNo, pageSize, sortBy, sortDirection, search, type, requestId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseWrapper<SocialFundDtos.FundResponse>> get(
            @PathVariable UUID id, HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return service.get(id, requestId);
    }

    @PostMapping
    public ResponseEntity<ResponseWrapper<SocialFundDtos.FundResponse>> create(
            @Valid @RequestBody SocialFundDtos.CreateRequest body, HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return service.create(body, requestId);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseWrapper<SocialFundDtos.FundResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody SocialFundDtos.UpdateRequest body,
            HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return service.update(id, body, requestId);
    }

    @PatchMapping("/{id}/archive")
    public ResponseEntity<ResponseWrapper<SocialFundDtos.ArchiveResponse>> toggleArchive(
            @PathVariable UUID id, HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return service.toggleArchive(id, requestId);
    }
}
