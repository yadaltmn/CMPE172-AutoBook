package com.autobook.service;

import com.autobook.dto.AvailabilitySlotDto;
import com.autobook.repository.SlotRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SlotService {

    private final SlotRepository slotRepository;

    public SlotService(SlotRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    public List<AvailabilitySlotDto> getAvailableSlots() {
        return slotRepository.findAvailableSlots();
    }
}
