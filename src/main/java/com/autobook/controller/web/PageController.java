package com.autobook.controller.web;

import com.autobook.dto.AvailabilitySlotDto;
import com.autobook.dto.PageDto;
import com.autobook.dto.SlotSearchCriteria;
import com.autobook.exception.InvalidRequestException;
import com.autobook.model.AppUser;
import com.autobook.service.CurrentUserService;
import com.autobook.service.HomeService;
import com.autobook.service.SlotService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PageController {

    private final HomeService homeService;
    private final SlotService slotService;
    private final CurrentUserService currentUserService;

    public PageController(HomeService homeService, SlotService slotService, CurrentUserService currentUserService) {
        this.homeService = homeService;
        this.slotService = slotService;
        this.currentUserService = currentUserService;
    }

    /**
     * Browser landing page. Requests that do not ask for HTML (curl, tests, API clients) still
     * reach the Milestone 1 JSON summary in {@code HomeController}.
     */
    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String home(Model model) {
        model.addAttribute("summary", homeService.getHomeSummary());
        model.addAttribute("services", slotService.getServices());
        model.addAttribute("nextSlots", slotService.searchAvailableSlots(
                new SlotSearchCriteria(null, null, null, 0, 3)).content());
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication) {
        AppUser currentUser = currentUserService.requireUser(authentication);
        return switch (currentUser.role()) {
            case CUSTOMER -> "redirect:/customer/dashboard";
            case PROVIDER -> "redirect:/provider/dashboard";
            case ADMIN -> "redirect:/admin/dashboard";
        };
    }

    @GetMapping("/browse")
    public String browse(@RequestParam(required = false) Long providerId,
                         @RequestParam(required = false) Long serviceId,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         @RequestParam(defaultValue = "0") int page,
                         Model model) {
        SlotSearchCriteria criteria = new SlotSearchCriteria(
                providerId, serviceId, date, Math.max(page, 0), SlotSearchCriteria.DEFAULT_PAGE_SIZE);
        PageDto<AvailabilitySlotDto> results;
        try {
            results = slotService.searchAvailableSlots(criteria);
        } catch (InvalidRequestException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            results = PageDto.of(List.of(), 0, SlotSearchCriteria.DEFAULT_PAGE_SIZE, 0);
        }
        model.addAttribute("results", results);
        model.addAttribute("criteria", criteria);
        model.addAttribute("providers", slotService.getProviders());
        model.addAttribute("services", slotService.getServices());
        model.addAttribute("today", LocalDate.now());
        return "browse";
    }

    @GetMapping("/access-denied")
    public String accessDenied(Model model) {
        model.addAttribute("status", 403);
        model.addAttribute("title", "Access denied");
        model.addAttribute("message", "Your account does not have permission to open this page.");
        return "error";
    }
}
