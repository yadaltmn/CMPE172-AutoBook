package com.autobook.service;

import com.autobook.dto.AppointmentDto;
import com.autobook.dto.BookingRequest;
import com.autobook.exception.BookingConflictException;
import com.autobook.exception.ForbiddenOperationException;
import com.autobook.exception.InvalidRequestException;
import com.autobook.exception.ResourceNotFoundException;
import com.autobook.model.AppUser;
import com.autobook.model.AppointmentStatus;
import com.autobook.model.SlotLock;
import com.autobook.model.UserRole;
import com.autobook.repository.AppointmentRepository;
import com.autobook.repository.SlotRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer booking workflow.
 *
 * <p>Double booking is prevented by three layers that all run inside one database transaction:
 * <ol>
 *     <li>{@code SELECT ... FOR UPDATE} on the slot row serializes concurrent bookings of the same slot.</li>
 *     <li>A conditional {@code UPDATE ... WHERE is_available = TRUE} claims the slot atomically.</li>
 *     <li>A unique constraint on the active appointment per slot rejects any duplicate that slips through.</li>
 * </ol>
 * If any step fails the whole transaction rolls back, so the slot and appointment tables never disagree.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final SlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;
    private final Clock clock;

    public BookingService(SlotRepository slotRepository, AppointmentRepository appointmentRepository, Clock clock) {
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentDto book(AppUser customer, BookingRequest request) {
        if (customer.role() != UserRole.CUSTOMER) {
            throw new ForbiddenOperationException("Only customer accounts can book appointments.");
        }
        long slotId = request.slotId();
        LocalDateTime now = LocalDateTime.now(clock);

        try {
            // Critical section starts here: the row lock is held until the transaction ends.
            SlotLock slot = slotRepository.lockById(slotId)
                    .orElseThrow(() -> new ResourceNotFoundException("Appointment slot " + slotId + " was not found."));

            if (request.serviceId() != null && !request.serviceId().equals(slot.serviceId())) {
                throw new InvalidRequestException("The selected service does not match this appointment slot.");
            }
            if (!slot.startTime().isAfter(now)) {
                throw new InvalidRequestException("This appointment slot has already started and can no longer be booked.");
            }
            if (!slot.available()) {
                throw new BookingConflictException("Sorry, this appointment slot has already been booked.");
            }
            if (slotRepository.claimIfAvailable(slotId, now) != 1) {
                throw new BookingConflictException("Sorry, this appointment slot has already been booked.");
            }

            long appointmentId = appointmentRepository.insert(
                    customer.userId(), slot.providerId(), slot.serviceId(), slotId, AppointmentStatus.BOOKED, now);

            log.info("Customer {} booked slot {} as appointment {}", customer.userId(), slotId, appointmentId);
            return appointmentRepository.findById(appointmentId).orElseThrow();
        } catch (DuplicateKeyException | ConcurrencyFailureException ex) {
            // Last line of defence: the database constraint or lock manager rejected a competing booking.
            log.info("Booking conflict for slot {}: {}", slotId, ex.getClass().getSimpleName());
            throw new BookingConflictException("Sorry, this appointment slot has already been booked.");
        }
    }
}
