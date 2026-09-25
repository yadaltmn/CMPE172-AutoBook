package com.autobook.controller;

import com.autobook.dto.AvailabilitySlotDto;
import com.autobook.service.SlotService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SlotController {

    private final SlotService slotService;

    public SlotController(SlotService slotService) {
        this.slotService = slotService;
    }

    @GetMapping("/slots")
    public List<AvailabilitySlotDto> getAvailableSlots() {
        return slotService.getAvailableSlots();
    }
}
