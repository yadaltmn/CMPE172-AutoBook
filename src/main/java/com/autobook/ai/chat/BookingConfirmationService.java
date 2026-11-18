package com.autobook.ai.chat;

import com.autobook.ai.AiProperties;
import com.autobook.dto.AppointmentDto;
import com.autobook.dto.BookingRequest;
import com.autobook.dto.SlotDetailsDto;
import com.autobook.exception.BookingConflictException;
import com.autobook.exception.InvalidRequestException;
import com.autobook.exception.ResourceNotFoundException;
import com.autobook.model.AppUser;
import com.autobook.notification.BookingNotifications;
import com.autobook.service.AppointmentService;
import com.autobook.service.BookingService;
import com.autobook.service.SlotService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Executes a booking or cancellation that the assistant prepared, after the customer explicitly
 * confirmed it. This is the only place the assistant's actions change data, and it does so only
 * through the existing transactional services:
 * <ul>
 *     <li>bookings through {@link BookingService#book}, which locks the slot row, re-checks that it is
 *     still open, and relies on the database's one-active-booking constraint, so a slot taken since the
 *     assistant offered it is rejected and alternatives are suggested;</li>
 *     <li>cancellations through {@link AppointmentService#cancel}, which re-checks ownership.</li>
 * </ul>
 */
@Service
public class BookingConfirmationService {

    public enum Status {
        BOOKED,
        CANCELLED,
        CONFLICT,
        DECLINED
    }

    public record Outcome(Status status, ChatReply reply) {
    }

    private static final Logger log = LoggerFactory.getLogger(BookingConfirmationService.class);

    private final PendingActionStore pendingActions;
    private final ConversationStore conversations;
    private final AgentToolsFactory toolsFactory;
    private final BookingService bookingService;
    private final AppointmentService appointmentService;
    private final SlotService slotService;
    private final AiProperties properties;
    private final MeterRegistry meterRegistry;

    public BookingConfirmationService(PendingActionStore pendingActions, ConversationStore conversations,
                                      AgentToolsFactory toolsFactory, BookingService bookingService,
                                      AppointmentService appointmentService, SlotService slotService,
                                      AiProperties properties, MeterRegistry meterRegistry) {
        this.pendingActions = pendingActions;
        this.conversations = conversations;
        this.toolsFactory = toolsFactory;
        this.bookingService = bookingService;
        this.appointmentService = appointmentService;
        this.slotService = slotService;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    public Outcome confirm(AppUser customer, String conversationId, String token) {
        ConversationalAgentService.requireCustomer(customer);
        PendingActionStore.PendingAction action = pendingActions.claim(token, customer.userId(), conversationId);
        Conversation conversation = conversations.get(customer.userId(), conversationId);
        synchronized (conversation) {
            Outcome outcome = switch (action.type()) {
                case BOOK -> book(customer, conversation, action);
                case CANCEL -> cancel(customer, conversation, action);
            };
            conversation.remember("Confirm: " + action.summary(), outcome.reply().reply(), properties.conversationTurns());
            meterRegistry.counter("autobook.ai.agent.confirmations", "outcome", outcome.status().name().toLowerCase())
                    .increment();
            return outcome;
        }
    }

    public Outcome decline(AppUser customer, String conversationId, String token) {
        ConversationalAgentService.requireCustomer(customer);
        PendingActionStore.PendingAction action = pendingActions.claim(token, customer.userId(), conversationId);
        Conversation conversation = conversations.get(customer.userId(), conversationId);
        synchronized (conversation) {
            String text = "OK, I did not make that change: " + action.summary() + ".";
            conversation.remember("Decline: " + action.summary(), text, properties.conversationTurns());
            meterRegistry.counter("autobook.ai.agent.confirmations", "outcome", "declined").increment();
            return new Outcome(Status.DECLINED, reply(conversationId, text, List.of()));
        }
    }

    private Outcome book(AppUser customer, Conversation conversation, PendingActionStore.PendingAction action) {
        try {
            AppointmentDto appointment = bookingService.book(customer, new BookingRequest(action.targetId(), null));
            log.info("Assistant booking confirmed: customer {} booked slot {} as appointment {}", customer.userId(),
                    action.targetId(), appointment.appointmentId());
            return new Outcome(Status.BOOKED, reply(conversation.conversationId(),
                    "Done! Your " + appointment.serviceName() + " at " + appointment.providerName() + " on "
                            + BookingAgentTools.DISPLAY.format(appointment.startTime()) + " is booked. Confirmation number: "
                            + BookingNotifications.confirmationNumber(appointment) + ".", List.of()));
        } catch (BookingConflictException | InvalidRequestException | ResourceNotFoundException ex) {
            log.info("Assistant booking for customer {} rejected for slot {}: {}", customer.userId(), action.targetId(),
                    ex.getClass().getSimpleName());
            List<SlotOption> alternatives = alternatives(customer, conversation, action.targetId());
            return new Outcome(Status.CONFLICT, reply(conversation.conversationId(),
                    "Sorry, that slot can no longer be booked: " + ex.getMessage() + " Other open times: "
                            + BookingAgentTools.describe(alternatives), alternatives));
        }
    }

    private Outcome cancel(AppUser customer, Conversation conversation, PendingActionStore.PendingAction action) {
        try {
            AppointmentDto cancelled = appointmentService.cancel(customer, action.targetId());
            log.info("Assistant cancellation confirmed: customer {} cancelled appointment {}", customer.userId(),
                    cancelled.appointmentId());
            return new Outcome(Status.CANCELLED, reply(conversation.conversationId(),
                    "Done. Your " + cancelled.serviceName() + " at " + cancelled.providerName() + " on "
                            + BookingAgentTools.DISPLAY.format(cancelled.startTime()) + " is cancelled ("
                            + BookingNotifications.confirmationNumber(cancelled) + ").", List.of()));
        } catch (BookingConflictException | InvalidRequestException ex) {
            return new Outcome(Status.CONFLICT, reply(conversation.conversationId(),
                    "Sorry, that appointment could not be cancelled: " + ex.getMessage(), List.of()));
        }
    }

    private List<SlotOption> alternatives(AppUser customer, Conversation conversation, long slotId) {
        Long serviceId;
        try {
            SlotDetailsDto slot = slotService.getSlotDetails(slotId);
            serviceId = slot.serviceId();
        } catch (ResourceNotFoundException ex) {
            serviceId = null;
        }
        return toolsFactory.create(customer, conversation).alternativesFor(serviceId, slotId);
    }

    private static ChatReply reply(String conversationId, String text, List<SlotOption> slots) {
        return new ChatReply(conversationId, text, List.of(), slots, null);
    }
}
