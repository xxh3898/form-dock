package com.formdock.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class CreatorAuthenticationController {

    private final CreatorSessionService creatorSessionService;

    public CreatorAuthenticationController(CreatorSessionService creatorSessionService) {
        this.creatorSessionService = creatorSessionService;
    }

    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getToken(), csrfToken.getHeaderName());
    }

    @PostMapping("/login")
    CreatorResponse login(
            @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        CreatorPrincipal creator = creatorSessionService.login(
                loginRequest.email(),
                loginRequest.password(),
                request,
                response);
        return CreatorResponse.from(creator);
    }

    @GetMapping("/me")
    CreatorResponse me(@AuthenticationPrincipal CreatorPrincipal creator) {
        return CreatorResponse.from(creator);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        creatorSessionService.logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    record LoginRequest(String email, String password) {

        @Override
        public String toString() {
            return "LoginRequest[credentials=REDACTED]";
        }
    }

    record CreatorResponse(Long id, String email, String displayName, UserRole role) {

        static CreatorResponse from(CreatorPrincipal creator) {
            return new CreatorResponse(
                    creator.id(),
                    creator.email(),
                    creator.displayName(),
                    creator.role());
        }
    }

    record CsrfResponse(String token, String headerName) {
    }
}
