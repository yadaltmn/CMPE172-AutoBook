package com.autobook.controller.web;

import com.autobook.dto.AppointmentDto;
import com.autobook.dto.AppointmentHistoryDto;
import com.autobook.dto.BookingRequest;
import com.autobook.dto.SlotDetailsDto;
import com.autobook.dto.SlotSearchCriteria;
import com.autobook.exception.BookingConflictException;
import com.autobook.exception.InvalidRequestException;
import com.autobook.exception.ResourceNotFoundException;
import com.autobook.model.AppUser;
import com.autobook.service.AppointmentService;
import com.autobook.service.BookingService;
import com.autobook.service.CurrentUserService;
import com.autobook.service.SlotService;
import java.time.LocalDateTime;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Customer pages: dashboard, booking review and confirmation, appointments, and cancellation.
 * The signed-in customer always comes from the security session, never from a form field.
 */
@Controller
@RequestMapping("/customer")
public class CustomerPageController {

    private final SlotService slotService;
    private final BookingService bookingService;
    private final AppointmentService appointmentService;
    private final CurrentUserService currentUserService;

    public CustomerPageController(SlotService slotService, BookingService bookingService,
                                  AppointmentService appointmentService, CurrentUserService currentUserService) {
        this.slotService = slotService;
        this.bookingService = bookingService;
        this.appointmentService = appointmentService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        AppUser customer = currentUserService.requireUser(authentication);
        AppointmentHistoryDto appointments = appointmentService.getAppointments(customer);
        model.addAttribute("appointments", appointments);
        model.addAttribute("nextAppointment", appointments.upcoming().stream().findFirst().orElse(null));
        model.addAttribute("openSlots", slotService.searchAvailableSlots(
                new SlotSearchCriteria(null, null, null, 0, 4)));
        return "customer/dashboard";
    }

    @GetMapping("/book/{slotId}")
    public String reviewBooking(@PathVariable long slotId, Model model, RedirectAttributes redirect) {
        SlotDetailsDto slot = slotService.getSlotDetails(slotId);
        if (!slot.available() || !slot.startTime().isAfter(LocalDateTime.now())) {
            redirect.addFlashAttribute("errorMessage", "Sorry, that appointment slot is no longer available.");
            return "redirect:/browse";
        }
        model.addAttribute("slot", slot);
        return "customer/book";
    }

    @PostMapping("/book")
    public String book(Authentication authentication,
                       @RequestParam long slotId,
                       @RequestParam(required = false) Long serviceId,
                       RedirectAttributes redirect) {
        AppUser customer = currentUserService.requireUser(authentication);
        try {
            AppointmentDto appointment = bookingService.book(customer, new BookingRequest(slotId, serviceId));
            return "redirect:/customer/appointments/" + appointment.appointmentId() + "/confirmation";
        } catch (BookingConflictException | InvalidRequestException | ResourceNotFoundException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/browse";
        }
    }

    @GetMapping("/appointments/{appointmentId}/confirmation")
    public String confirmation(Authentication authentication,
                               @PathVariable long appointmentId, Model model) {
        AppUser customer = currentUserService.requireUser(authentication);
        model.addAttribute("appointment", appointmentService.getAppointment(customer, appointmentId));
        return "customer/confirmation";
    }

    @GetMapping("/appointments")
    public String appointments(Authentication authentication, Model model) {
        AppUser customer = currentUserService.requireUser(authentication);
        model.addAttribute("appointments", appointmentService.getAppointments(customer));
        return "customer/appointments";
    }

    @GetMapping("/appointments/{appointmentId}/cancel")
    public String confirmCancel(Authentication authentication,
                                @PathVariable long appointmentId, Model model, RedirectAttributes redirect) {
        AppUser customer = currentUserService.requireUser(authentication);
        AppointmentDto appointment = appointmentService.getAppointment(customer, appointmentId);
        if (!appointmentService.isCancellable(appointment)) {
            redirect.addFlashAttribute("errorMessage", "This appointment can no longer be cancelled.");
            return "redirect:/customer/appointments";
        }
        model.addAttribute("appointment", appointment);
        return "customer/cancel";
    }

    @PostMapping("/appointments/{appointmentId}/cancel")
    public String cancel(Authentication authentication,
                         @PathVariable long appointmentId, RedirectAttributes redirect) {
        AppUser customer = currentUserService.requireUser(authentication);
        try {
            AppointmentDto cancelled = appointmentService.cancel(customer, appointmentId);
            redirect.addFlashAttribute("successMessage", "Your " + cancelled.serviceName() + " appointment with "
                    + cancelled.providerName() + " has been cancelled.");
        } catch (BookingConflictException | InvalidRequestException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/customer/appointments";
    }
}
