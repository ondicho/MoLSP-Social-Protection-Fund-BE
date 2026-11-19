package MoLSP.go.ke.Social_Assistance_Fund.controllers;

import MoLSP.go.ke.Social_Assistance_Fund.dtos.AdminUserDtos;
import MoLSP.go.ke.Social_Assistance_Fund.dtos.PaginatedResponse;
import MoLSP.go.ke.Social_Assistance_Fund.dtos.ResponseWrapper;
import MoLSP.go.ke.Social_Assistance_Fund.enums.Role;
import MoLSP.go.ke.Social_Assistance_Fund.services.AdminUserService;
import MoLSP.go.ke.Social_Assistance_Fund.utils.ResponseHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminUserController {
    private final AdminUserService service;
    private final ResponseHandler responses;

    @GetMapping
    public ResponseEntity<ResponseWrapper<PaginatedResponse<AdminUserDtos.UserResponse>>> list(
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean enabled,
            HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return service.list(pageNo, pageSize, sortBy, sortDirection, search, role, enabled, requestId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseWrapper<AdminUserDtos.UserResponse>> get(
            @PathVariable UUID id, HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return service.get(id, requestId);
    }

    @PostMapping
    public ResponseEntity<ResponseWrapper<AdminUserDtos.UserResponse>> create(
            @Valid @RequestBody AdminUserDtos.CreateRequest body, HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return service.create(body, requestId);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseWrapper<AdminUserDtos.UserResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody AdminUserDtos.UpdateRequest body,
            HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return service.update(id, body, requestId);
    }

    @PatchMapping("/{id}/enabled")
    public ResponseEntity<ResponseWrapper<AdminUserDtos.UserResponse>> setEnabled(
            @PathVariable UUID id, @RequestParam boolean enabled, HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return service.setEnabled(id, enabled, requestId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseWrapper<AdminUserDtos.UserResponse>> deactivate(
            @PathVariable UUID id, HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return service.setEnabled(id, false, requestId);
    }
}
