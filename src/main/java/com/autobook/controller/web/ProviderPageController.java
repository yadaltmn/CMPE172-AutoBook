package com.autobook.controller.web;

import com.autobook.dto.AppointmentDto;
import com.autobook.dto.ProviderDto;
import com.autobook.dto.ProviderSlotDto;
import com.autobook.dto.SlotForm;
import com.autobook.dto.SlotRemovalDto;
import com.autobook.exception.BookingConflictException;
import com.autobook.exception.InvalidRequestException;
import com.autobook.model.AppointmentStatus;
import com.autobook.service.CurrentUserService;
import com.autobook.service.ProviderService;
import com.autobook.service.SlotService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Provider pages. The provider is always resolved from the signed-in account.
 */
@Controller
@RequestMapping("/provider")
public class ProviderPageController {

    private final CurrentUserService currentUserService;
    private final ProviderService providerService;
    private final SlotService slotService;

    public ProviderPageController(CurrentUserService currentUserService, ProviderService providerService,
                                  SlotService slotService) {
        this.currentUserService = currentUserService;
        this.providerService = providerService;
        this.slotService = slotService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        ProviderDto provider = currentUserService.requireProvider(authentication);
        List<ProviderSlotDto> slots = providerService.getSlots(provider);
        List<AppointmentDto> appointments = providerService.getAppointments(provider);
        LocalDateTime now = LocalDateTime.now();

        model.addAttribute("provider", provider);
        model.addAttribute("openCount", slots.stream().filter(slot -> "AVAILABLE".equals(slot.state())).count());
        model.addAttribute("bookedCount", slots.stream().filter(slot -> "BOOKED".equals(slot.state())).count());
        model.addAttribute("completedCount", slots.stream().filter(slot -> "COMPLETED".equals(slot.state())).count());
        model.addAttribute("upcoming", appointments.stream()
                .filter(appointment -> appointment.status() == AppointmentStatus.BOOKED && appointment.startTime().isAfter(now))
                .limit(5)
                .toList());
        return "provider/dashboard";
    }

    @GetMapping("/availability")
    public String availability(Authentication authentication, Model model) {
        if (!model.containsAttribute("slotForm")) {
            SlotForm form = new SlotForm();
            form.setDate(LocalDate.now().plusDays(1));
            model.addAttribute("slotForm", form);
        }
        populateAvailability(currentUserService.requireProvider(authentication), model);
        return "provider/availability";
    }

    @PostMapping("/availability")
    public String createSlot(Authentication authentication,
                             @Valid @ModelAttribute("slotForm") SlotForm slotForm,
                             BindingResult bindingResult,
                             Model model,
                             RedirectAttributes redirect) {
        ProviderDto provider = currentUserService.requireProvider(authentication);
        if (bindingResult.hasErrors()) {
            populateAvailability(provider, model);
            return "provider/availability";
        }
        try {
            ProviderSlotDto slot = providerService.createSlot(provider, slotForm.toRequest());
            redirect.addFlashAttribute("successMessage", "Added " + slot.serviceName() + " availability.");
            return "redirect:/provider/availability";
        } catch (InvalidRequestException | BookingConflictException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            populateAvailability(provider, model);
            return "provider/availability";
        }
    }

    @PostMapping("/availability/{slotId}/delete")
    public String removeSlot(Authentication authentication, @PathVariable long slotId, RedirectAttributes redirect) {
        try {
            SlotRemovalDto result = providerService.removeSlot(currentUserService.requireProvider(authentication), slotId);
            redirect.addFlashAttribute("successMessage", result.message());
        } catch (BookingConflictException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/provider/availability";
    }

    @GetMapping("/appointments")
    public String appointments(Authentication authentication, Model model) {
        ProviderDto provider = currentUserService.requireProvider(authentication);
        model.addAttribute("provider", provider);
        model.addAttribute("appointments", providerService.getAppointments(provider));
        model.addAttribute("now", LocalDateTime.now());
        return "provider/appointments";
    }

    @PostMapping("/appointments/{appointmentId}/complete")
    public String complete(Authentication authentication, @PathVariable long appointmentId, RedirectAttributes redirect) {
        try {
            providerService.completeAppointment(currentUserService.requireProvider(authentication), appointmentId);
            redirect.addFlashAttribute("successMessage", "Appointment marked as completed.");
        } catch (InvalidRequestException | BookingConflictException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/provider/appointments";
    }

    private void populateAvailability(ProviderDto provider, Model model) {
        model.addAttribute("provider", provider);
        model.addAttribute("slots", providerService.getSlots(provider));
        model.addAttribute("services", slotService.getServices());
        model.addAttribute("today", LocalDate.now());
    }
}
