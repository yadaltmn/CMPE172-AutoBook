package com.autobook.service;

import com.autobook.dto.AvailabilitySlotDto;
import com.autobook.dto.PageDto;
import com.autobook.dto.ProviderDto;
import com.autobook.dto.ServiceDto;
import com.autobook.dto.SlotDetailsDto;
import com.autobook.dto.SlotSearchCriteria;
import com.autobook.exception.InvalidRequestException;
import com.autobook.exception.ResourceNotFoundException;
import com.autobook.repository.ProviderRepository;
import com.autobook.repository.ServiceRepository;
import com.autobook.repository.SlotRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SlotService {

    private final SlotRepository slotRepository;
    private final ProviderRepository providerRepository;
    private final ServiceRepository serviceRepository;
    private final Clock clock;

    public SlotService(SlotRepository slotRepository, ProviderRepository providerRepository,
                       ServiceRepository serviceRepository, Clock clock) {
        this.slotRepository = slotRepository;
        this.providerRepository = providerRepository;
        this.serviceRepository = serviceRepository;
        this.clock = clock;
    }

    public List<AvailabilitySlotDto> getAvailableSlots() {
        return slotRepository.findAvailableSlots(LocalDateTime.now(clock));
    }

    public PageDto<AvailabilitySlotDto> searchAvailableSlots(SlotSearchCriteria criteria) {
        validate(criteria);
        LocalDateTime now = LocalDateTime.now(clock);
        long total = slotRepository.countAvailableSlots(criteria, now);
        List<AvailabilitySlotDto> content = slotRepository.searchAvailableSlots(criteria, now);
        return PageDto.of(content, criteria.page(), criteria.size(), total);
    }

    public SlotDetailsDto getSlotDetails(long slotId) {
        return slotRepository.findDetailsById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment slot " + slotId + " was not found."));
    }

    public List<ProviderDto> getProviders() {
        return providerRepository.findAll();
    }

    public List<ServiceDto> getServices() {
        return serviceRepository.findAll();
    }

    private void validate(SlotSearchCriteria criteria) {
        if (criteria.page() < 0) {
            throw new InvalidRequestException("Page must be zero or greater.");
        }
        if (criteria.size() < 1 || criteria.size() > SlotSearchCriteria.MAX_PAGE_SIZE) {
            throw new InvalidRequestException("Page size must be between 1 and " + SlotSearchCriteria.MAX_PAGE_SIZE + ".");
        }
        if (criteria.providerId() != null && criteria.providerId() < 1) {
            throw new InvalidRequestException("Provider id must be a positive number.");
        }
        if (criteria.serviceId() != null && criteria.serviceId() < 1) {
            throw new InvalidRequestException("Service id must be a positive number.");
        }
    }
}
