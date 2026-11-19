package MoLSP.go.ke.Social_Assistance_Fund.controllers;

import MoLSP.go.ke.Social_Assistance_Fund.dtos.ApplicationDtos;
import MoLSP.go.ke.Social_Assistance_Fund.dtos.PaginatedResponse;
import MoLSP.go.ke.Social_Assistance_Fund.dtos.ResponseWrapper;
import MoLSP.go.ke.Social_Assistance_Fund.enums.ApplicationStatus;
import MoLSP.go.ke.Social_Assistance_Fund.services.ApplicationService;
import MoLSP.go.ke.Social_Assistance_Fund.utils.ResponseHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {
    private final ApplicationService service;
    private final ResponseHandler responses;

    @PostMapping("/self-registration")
    public ResponseEntity<ResponseWrapper<ApplicationDtos.RegistrationReceipt>> selfRegister(
            @Valid @RequestBody ApplicationDtos.FormRequest body, HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return service.selfRegister(body, requestId);
    }

    @PostMapping
    @PreAuthorize("hasRole('DATA_ENTRY_OFFICER')")
    public ResponseEntity<ResponseWrapper<ApplicationDtos.ApplicationResponse>> createAssisted(
            @Valid @RequestBody ApplicationDtos.FormRequest body, HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return service.createAssisted(body, requestId);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','APPROVER','DATA_ENTRY_OFFICER')")
    public ResponseEntity<ResponseWrapper<PaginatedResponse<ApplicationDtos.ApplicationResponse>>> list(
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate applicationDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate applicationDateTo,
            @RequestParam(required = false) String programme,
            @RequestParam(required = false) Integer countyId,
            @RequestParam(required = false) Integer subCountyId,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String subLocation,
            @RequestParam(required = false) String village,
            HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return service.list(pageNo, pageSize, sortBy, sortDirection, search, status,
                applicationDateFrom, applicationDateTo, programme, countyId, subCountyId,
                location, subLocation, village, requestId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','APPROVER','DATA_ENTRY_OFFICER')")
    public ResponseEntity<ResponseWrapper<ApplicationDtos.ApplicationResponse>> get(
            @PathVariable UUID id, HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return service.get(id, requestId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DATA_ENTRY_OFFICER')")
    public ResponseEntity<ResponseWrapper<ApplicationDtos.ApplicationResponse>> updateDraft(
            @PathVariable UUID id, @Valid @RequestBody ApplicationDtos.UpdateRequest body,
            HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return service.updateDraft(id, body, requestId);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('DATA_ENTRY_OFFICER')")
    public ResponseEntity<ResponseWrapper<ApplicationDtos.ApplicationResponse>> submit(
            @PathVariable UUID id, @Valid @RequestBody ApplicationDtos.ActionRequest body,
            HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return service.submit(id, body, requestId);
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('APPROVER')")
    public ResponseEntity<ResponseWrapper<ApplicationDtos.ApplicationResponse>> startReview(
            @PathVariable UUID id, @Valid @RequestBody ApplicationDtos.ActionRequest body,
            HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return service.startReview(id, body, requestId);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('APPROVER')")
    public ResponseEntity<ResponseWrapper<ApplicationDtos.ApplicationResponse>> approve(
            @PathVariable UUID id, @Valid @RequestBody ApplicationDtos.ApprovalRequest body,
            HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return service.approve(id, body, requestId);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('APPROVER')")
    public ResponseEntity<ResponseWrapper<ApplicationDtos.ApplicationResponse>> reject(
            @PathVariable UUID id, @Valid @RequestBody ApplicationDtos.DecisionRequest body,
            HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return service.reject(id, body, requestId);
    }

    @PostMapping("/{id}/return-for-correction")
    @PreAuthorize("hasRole('APPROVER')")
    public ResponseEntity<ResponseWrapper<ApplicationDtos.ApplicationResponse>> returnForCorrection(
            @PathVariable UUID id, @Valid @RequestBody ApplicationDtos.DecisionRequest body,
            HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return service.returnForCorrection(id, body, requestId);
    }
}
