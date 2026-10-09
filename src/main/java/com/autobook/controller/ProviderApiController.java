package com.autobook.controller;

import com.autobook.dto.AppointmentDto;
import com.autobook.dto.CreateSlotRequest;
import com.autobook.dto.ProviderDto;
import com.autobook.dto.ProviderSlotDto;
import com.autobook.dto.SlotRemovalDto;
import com.autobook.service.CurrentUserService;
import com.autobook.service.ProviderService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provider")
public class ProviderApiController {

    private final CurrentUserService currentUserService;
    private final ProviderService providerService;

    public ProviderApiController(CurrentUserService currentUserService, ProviderService providerService) {
        this.currentUserService = currentUserService;
        this.providerService = providerService;
    }

    @GetMapping("/profile")
    public ProviderDto profile(Authentication authentication) {
        return currentUserService.requireProvider(authentication);
    }

    @GetMapping("/slots")
    public List<ProviderSlotDto> slots(Authentication authentication) {
        return providerService.getSlots(currentUserService.requireProvider(authentication));
    }

    @PostMapping("/slots")
    public ResponseEntity<ProviderSlotDto> createSlot(@Valid @RequestBody CreateSlotRequest request,
                                                      Authentication authentication) {
        ProviderSlotDto slot = providerService.createSlot(currentUserService.requireProvider(authentication), request);
        return ResponseEntity.created(URI.create("/api/slots/" + slot.slotId())).body(slot);
    }

    @DeleteMapping("/slots/{slotId}")
    public SlotRemovalDto removeSlot(@PathVariable long slotId, Authentication authentication) {
        return providerService.removeSlot(currentUserService.requireProvider(authentication), slotId);
    }

    @GetMapping("/appointments")
    public List<AppointmentDto> appointments(Authentication authentication) {
        return providerService.getAppointments(currentUserService.requireProvider(authentication));
    }

    @PostMapping("/appointments/{appointmentId}/complete")
    public AppointmentDto completeAppointment(@PathVariable long appointmentId, Authentication authentication) {
        return providerService.completeAppointment(currentUserService.requireProvider(authentication), appointmentId);
    }
}
