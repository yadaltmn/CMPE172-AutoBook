package com.autobook.controller.web;

import com.autobook.dto.ProviderDto;
import com.autobook.dto.ProviderUpdateRequest;
import com.autobook.exception.ResourceNotFoundException;
import com.autobook.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminPageController {

    private final AdminService adminService;

    public AdminPageController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("providers", adminService.getProviders());
        model.addAttribute("users", adminService.getUsers());
        model.addAttribute("appointments", adminService.getAppointments());
        return "admin/dashboard";
    }

    @GetMapping("/providers/{providerId}/edit")
    public String editProvider(@PathVariable long providerId, Model model) {
        ProviderDto provider = adminService.getProvider(providerId);
        model.addAttribute("provider", provider);
        if (!model.containsAttribute("providerForm")) {
            model.addAttribute("providerForm", new ProviderUpdateRequest(provider.name(), provider.phone()));
        }
        return "admin/provider-edit";
    }

    @PostMapping("/providers/{providerId}")
    public String updateProvider(@PathVariable long providerId,
                                 @Valid @ModelAttribute("providerForm") ProviderUpdateRequest providerForm,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("provider", adminService.getProvider(providerId));
            return "admin/provider-edit";
        }
        ProviderDto updated = adminService.updateProvider(providerId, providerForm.name(), providerForm.phone());
        redirect.addFlashAttribute("successMessage", "Saved changes to " + updated.name() + ".");
        return "redirect:/admin/dashboard";
    }
}
