package com.autobook.controller;

import com.autobook.dto.AppointmentDto;
import com.autobook.dto.AppointmentHistoryDto;
import com.autobook.dto.BookingRequest;
import com.autobook.service.AppointmentService;
import com.autobook.service.BookingService;
import com.autobook.service.CurrentUserService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentApiController {

    private final BookingService bookingService;
    private final AppointmentService appointmentService;
    private final CurrentUserService currentUserService;

    public AppointmentApiController(BookingService bookingService, AppointmentService appointmentService,
                                    CurrentUserService currentUserService) {
        this.bookingService = bookingService;
        this.appointmentService = appointmentService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public ResponseEntity<AppointmentDto> book(@Valid @RequestBody BookingRequest request, Authentication authentication) {
        AppointmentDto appointment = bookingService.book(currentUserService.requireUser(authentication), request);
        return ResponseEntity.created(URI.create("/api/appointments/" + appointment.appointmentId())).body(appointment);
    }

    @GetMapping("/me")
    public AppointmentHistoryDto myAppointments(Authentication authentication) {
        return appointmentService.getAppointments(currentUserService.requireUser(authentication));
    }

    @GetMapping("/{appointmentId}")
    public AppointmentDto appointment(@PathVariable long appointmentId, Authentication authentication) {
        return appointmentService.getAppointment(currentUserService.requireUser(authentication), appointmentId);
    }

    @PostMapping("/{appointmentId}/cancel")
    public AppointmentDto cancel(@PathVariable long appointmentId, Authentication authentication) {
        return appointmentService.cancel(currentUserService.requireUser(authentication), appointmentId);
    }
}
