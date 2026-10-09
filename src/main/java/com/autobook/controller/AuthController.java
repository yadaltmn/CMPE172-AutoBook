
package com.autobook.controller;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    @GetMapping("/api/me")
    public Map<String, Object> currentUser(Authentication authentication) {
        return Map.of(
            "email", authentication.getName(),
            "roles", authentication.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority())
                .toList()
        );
    }

    /**
     * Returns the session's CSRF token so API clients (curl, Postman, JavaScript) can send
     * state-changing requests. Browser forms receive the token automatically through Thymeleaf.
     */
    @GetMapping("/api/csrf")
    public Map<String, String> csrfToken(CsrfToken csrfToken) {
        return Map.of(
            "headerName", csrfToken.getHeaderName(),
            "parameterName", csrfToken.getParameterName(),
            "token", csrfToken.getToken()
        );
    }
}
