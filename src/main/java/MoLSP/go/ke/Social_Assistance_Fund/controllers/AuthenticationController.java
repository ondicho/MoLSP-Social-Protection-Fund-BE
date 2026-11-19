package MoLSP.go.ke.Social_Assistance_Fund.controllers;

import MoLSP.go.ke.Social_Assistance_Fund.dtos.AuthDtos;
import MoLSP.go.ke.Social_Assistance_Fund.dtos.ResponseWrapper;
import MoLSP.go.ke.Social_Assistance_Fund.services.AuthenticationService;
import MoLSP.go.ke.Social_Assistance_Fund.utils.ResponseHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationController {
    private final AuthenticationService service;
    private final ResponseHandler responses;

    @PostMapping("/login")
    public ResponseEntity<ResponseWrapper<AuthDtos.LoginResponse>> login(
            @Valid @RequestBody AuthDtos.LoginRequest body, HttpServletRequest request) {
        String requestId = responses.requestId(request);
        return responses.success(service.login(body), "Login successful", requestId);
    }
}
