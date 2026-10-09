package com.autobook.controller;

import com.autobook.dto.ProviderDto;
import com.autobook.service.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provider")
public class ProviderApiController {

    private final CurrentUserService currentUserService;

    public ProviderApiController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/profile")
    public ProviderDto profile(Authentication authentication) {
        return currentUserService.requireProvider(authentication);
    }
}
