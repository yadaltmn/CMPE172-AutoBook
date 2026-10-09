package com.autobook.service;

import com.autobook.dto.AppointmentDto;
import com.autobook.dto.AppointmentHistoryDto;
import com.autobook.exception.BookingConflictException;
import com.autobook.exception.ForbiddenOperationException;
import com.autobook.exception.InvalidRequestException;
import com.autobook.exception.ResourceNotFoundException;
import com.autobook.model.AppUser;
import com.autobook.model.AppointmentStatus;
import com.autobook.model.SlotLock;
import com.autobook.repository.AppointmentRepository;
import com.autobook.repository.SlotRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer-facing appointment views and cancellation. Every method checks that the
 * appointment belongs to the signed-in customer.
 */
@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    private final AppointmentRepository appointmentRepository;
    private final SlotRepository slotRepository;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointmentRepository, SlotRepository slotRepository, Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.clock = clock;
    }

    public AppointmentHistoryDto getAppointments(AppUser customer) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<AppointmentDto> all = appointmentRepository.findByUserId(customer.userId());

        List<AppointmentDto> upcoming = all.stream()
                .filter(appointment -> isUpcoming(appointment, now))
                .sorted(Comparator.comparing(AppointmentDto::startTime))
                .toList();
        List<AppointmentDto> history = all.stream()
                .filter(appointment -> !isUpcoming(appointment, now))
                .toList();
        return new AppointmentHistoryDto(upcoming, history);
    }

    public AppointmentDto getAppointment(AppUser customer, long appointmentId) {
        AppointmentDto appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> notFound(appointmentId));
        requireOwner(customer, appointment);
        return appointment;
    }

    public boolean isCancellable(AppointmentDto appointment) {
        return isUpcoming(appointment, LocalDateTime.now(clock));
    }

    /**
     * Cancels a booked, future appointment and reopens its slot in one transaction.
     *
     * <p>Locks are taken in the same order as {@link BookingService#book}: first the slot row, then
     * the appointment row. A booking and a cancellation for the same slot therefore run one after
     * the other and cannot deadlock or leave the slot and appointment out of sync.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentDto cancel(AppUser customer, long appointmentId) {
        AppointmentDto appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> notFound(appointmentId));
        requireOwner(customer, appointment);

        SlotLock slot = slotRepository.lockById(appointment.slotId()).orElseThrow(() -> notFound(appointmentId));
        AppointmentStatus currentStatus = appointmentRepository.lockStatusById(appointmentId)
                .orElseThrow(() -> notFound(appointmentId));

        if (currentStatus != AppointmentStatus.BOOKED) {
            throw new BookingConflictException("Only booked appointments can be cancelled. This appointment is "
                    + currentStatus.name().toLowerCase() + ".");
        }
        if (!slot.startTime().isAfter(LocalDateTime.now(clock))) {
            throw new InvalidRequestException("Appointments that have already started cannot be cancelled.");
        }
        if (appointmentRepository.updateStatus(appointmentId, AppointmentStatus.BOOKED, AppointmentStatus.CANCELLED) != 1) {
            throw new BookingConflictException("This appointment was changed by another request. Please refresh.");
        }
        slotRepository.reopen(appointment.slotId());

        log.info("Customer {} cancelled appointment {} and reopened slot {}",
                customer.userId(), appointmentId, appointment.slotId());
        return appointmentRepository.findById(appointmentId).orElseThrow();
    }

    private boolean isUpcoming(AppointmentDto appointment, LocalDateTime now) {
        return appointment.status() == AppointmentStatus.BOOKED && appointment.startTime().isAfter(now);
    }

    private void requireOwner(AppUser customer, AppointmentDto appointment) {
        if (!appointment.customerId().equals(customer.userId())) {
            throw new ForbiddenOperationException("You can only view or change your own appointments.");
        }
    }

    private ResourceNotFoundException notFound(long appointmentId) {
        return new ResourceNotFoundException("Appointment " + appointmentId + " was not found.");
    }
}
