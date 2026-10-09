package com.autobook.controller;

import com.autobook.dto.AvailabilitySlotDto;
import com.autobook.dto.PageDto;
import com.autobook.dto.ProviderDto;
import com.autobook.dto.ServiceDto;
import com.autobook.dto.SlotDetailsDto;
import com.autobook.dto.SlotSearchCriteria;
import com.autobook.service.SlotService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SlotApiController {

    private final SlotService slotService;

    public SlotApiController(SlotService slotService) {
        this.slotService = slotService;
    }

    @GetMapping("/slots")
    public PageDto<AvailabilitySlotDto> searchSlots(
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + SlotSearchCriteria.DEFAULT_PAGE_SIZE) int size) {
        return slotService.searchAvailableSlots(new SlotSearchCriteria(providerId, serviceId, date, page, size));
    }

    @GetMapping("/slots/{slotId}")
    public SlotDetailsDto slot(@PathVariable long slotId) {
        return slotService.getSlotDetails(slotId);
    }

    @GetMapping("/providers")
    public List<ProviderDto> providers() {
        return slotService.getProviders();
    }

    @GetMapping("/services")
    public List<ServiceDto> services() {
        return slotService.getServices();
    }
}
