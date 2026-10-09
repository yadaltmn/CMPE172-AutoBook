package com.autobook.service;

import com.autobook.dto.AppointmentDto;
import com.autobook.dto.CreateSlotRequest;
import com.autobook.dto.ProviderDto;
import com.autobook.dto.ProviderSlotDto;
import com.autobook.dto.ServiceDto;
import com.autobook.dto.SlotRemovalDto;
import com.autobook.exception.BookingConflictException;
import com.autobook.exception.ForbiddenOperationException;
import com.autobook.exception.InvalidRequestException;
import com.autobook.exception.ResourceNotFoundException;
import com.autobook.model.AppointmentStatus;
import com.autobook.model.SlotLock;
import com.autobook.repository.AppointmentRepository;
import com.autobook.repository.ProviderRepository;
import com.autobook.repository.ServiceRepository;
import com.autobook.repository.SlotRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provider schedule management. Every operation is scoped to the provider linked to the
 * signed-in account; slot and appointment ids that belong to another provider are rejected.
 */
@Service
public class ProviderService {

    private static final Logger log = LoggerFactory.getLogger(ProviderService.class);
    private static final Duration MAX_SLOT_LENGTH = Duration.ofHours(8);

    private final SlotRepository slotRepository;
    private final ServiceRepository serviceRepository;
    private final ProviderRepository providerRepository;
    private final AppointmentRepository appointmentRepository;
    private final Clock clock;

    public ProviderService(SlotRepository slotRepository, ServiceRepository serviceRepository,
                           ProviderRepository providerRepository, AppointmentRepository appointmentRepository,
                           Clock clock) {
        this.slotRepository = slotRepository;
        this.serviceRepository = serviceRepository;
        this.providerRepository = providerRepository;
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
    }

    public List<ProviderSlotDto> getSlots(ProviderDto provider) {
        return slotRepository.findByProviderId(provider.providerId(), LocalDateTime.now(clock));
    }

    public List<AppointmentDto> getAppointments(ProviderDto provider) {
        return appointmentRepository.findByProviderId(provider.providerId());
    }

    /**
     * Creates an open slot. The provider row is locked first so two simultaneous requests from the
     * same provider cannot both pass the overlap check and insert overlapping slots.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProviderSlotDto createSlot(ProviderDto provider, CreateSlotRequest request) {
        LocalDateTime start = request.startTime().withSecond(0).withNano(0);
        LocalDateTime end = request.endTime().withSecond(0).withNano(0);

        if (!start.isBefore(end)) {
            throw new InvalidRequestException("The start time must be before the end time.");
        }
        if (!start.isAfter(LocalDateTime.now(clock))) {
            throw new InvalidRequestException("Availability must be scheduled in the future.");
        }
        if (Duration.between(start, end).compareTo(MAX_SLOT_LENGTH) > 0) {
            throw new InvalidRequestException("A single slot cannot be longer than 8 hours.");
        }
        ServiceDto service = serviceRepository.findById(request.serviceId())
                .orElseThrow(() -> new InvalidRequestException("The selected service does not exist."));
        if (Duration.between(start, end).toMinutes() < service.durationMinutes()) {
            throw new InvalidRequestException(service.name() + " needs at least "
                    + service.durationMinutes() + " minutes.");
        }

        providerRepository.lockProvider(provider.providerId());
        if (slotRepository.countOverlapping(provider.providerId(), start, end) > 0) {
            throw new BookingConflictException("This time overlaps another slot on your schedule.");
        }
        long slotId = slotRepository.insert(provider.providerId(), service.serviceId(), start, end);
        log.info("Provider {} created slot {} ({} - {})", provider.providerId(), slotId, start, end);

        return getSlots(provider).stream()
                .filter(slot -> slot.slotId() == slotId)
                .findFirst()
                .orElseThrow();
    }

    /**
     * Removes an open slot. The slot row is locked so a customer cannot book it while it is being
     * removed. Slots with cancelled-appointment history are closed instead of deleted so the
     * history stays intact.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SlotRemovalDto removeSlot(ProviderDto provider, long slotId) {
        SlotLock slot = slotRepository.lockById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot " + slotId + " was not found."));
        if (!slot.providerId().equals(provider.providerId())) {
            throw new ForbiddenOperationException("You can only manage your own availability.");
        }
        if (appointmentRepository.countActiveForSlot(slotId) > 0 || !slot.available()) {
            throw new BookingConflictException("This slot is booked or closed and cannot be removed.");
        }

        if (slotRepository.countAppointments(slotId) > 0) {
            slotRepository.close(slotId);
            log.info("Provider {} closed slot {} (kept for appointment history)", provider.providerId(), slotId);
            return new SlotRemovalDto(slotId, "CLOSED",
                    "The slot was closed. It is kept because it has cancelled appointment history.");
        }
        slotRepository.delete(slotId);
        log.info("Provider {} deleted slot {}", provider.providerId(), slotId);
        return new SlotRemovalDto(slotId, "DELETED", "The slot was removed from your schedule.");
    }

    /**
     * Marks a booked appointment that has already taken place as completed.
     */
    @Transactional
    public AppointmentDto completeAppointment(ProviderDto provider, long appointmentId) {
        AppointmentDto appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment " + appointmentId + " was not found."));
        if (!appointment.providerId().equals(provider.providerId())) {
            throw new ForbiddenOperationException("You can only manage your own appointments.");
        }
        if (appointment.startTime().isAfter(LocalDateTime.now(clock))) {
            throw new InvalidRequestException("Only appointments that have started can be marked completed.");
        }
        if (appointmentRepository.updateStatus(appointmentId, AppointmentStatus.BOOKED, AppointmentStatus.COMPLETED) != 1) {
            throw new BookingConflictException("Only booked appointments can be marked completed.");
        }
        return appointmentRepository.findById(appointmentId).orElseThrow();
    }
}
