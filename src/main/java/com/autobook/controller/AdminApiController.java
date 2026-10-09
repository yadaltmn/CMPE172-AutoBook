package com.autobook.controller;

import com.autobook.dto.ProviderDto;
import com.autobook.dto.ProviderUpdateRequest;
import com.autobook.model.AppUser;
import com.autobook.service.AdminService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminApiController {

    private final AdminService adminService;

    public AdminApiController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/providers")
    public List<ProviderDto> providers() {
        return adminService.getProviders();
    }

    @PutMapping("/providers/{providerId}")
    public ProviderDto updateProvider(@PathVariable long providerId, @Valid @RequestBody ProviderUpdateRequest request) {
        return adminService.updateProvider(providerId, request.name(), request.phone());
    }

    @GetMapping("/users")
    public List<AppUser> users() {
        return adminService.getUsers();
    }
}
