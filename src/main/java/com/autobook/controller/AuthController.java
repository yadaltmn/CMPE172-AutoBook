
package com.autobook.controller;

import java.util.Map;

import org.springframework.security.core.Authentication;
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
}
