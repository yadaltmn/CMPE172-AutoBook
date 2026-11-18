package com.autobook.ai.chat;

import com.autobook.ai.rag.KnowledgeBaseService;
import com.autobook.ai.rag.RetrievedPassage;
import com.autobook.dto.AppointmentDto;
import com.autobook.dto.AppointmentHistoryDto;
import com.autobook.dto.AvailabilitySlotDto;
import com.autobook.dto.ProviderDto;
import com.autobook.dto.ServiceDto;
import com.autobook.dto.SlotDetailsDto;
import com.autobook.dto.SlotSearchCriteria;
import com.autobook.exception.ForbiddenOperationException;
import com.autobook.exception.ResourceNotFoundException;
import com.autobook.model.AppUser;
import com.autobook.model.AppointmentStatus;
import com.autobook.notification.BookingNotifications;
import com.autobook.service.AppointmentService;
import com.autobook.service.SlotService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * The tools Gemini may call while answering one chat message.
 *
 * <p>A new instance is created for every message and is bound to the customer taken from the
 * verified Spring Security principal. No tool takes a user id, so the model cannot act for anyone
 * else. Every tool calls AutoBook's existing service layer, which keeps enforcing ownership and
 * business rules. Read-only tools return data; tools that would change data only prepare an action
 * that the customer must confirm (see {@link BookingConfirmationService}).
 *
 * <p>Each instance allows at most {@code autobook.ai.max-tool-calls-per-message} tool calls.
 */
public class BookingAgentTools {

    static final int MAX_SLOTS_RETURNED = 8;
    static final int MAX_SEARCH_DAYS = 31;
    static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("EEE MMM d, yyyy h:mm a", Locale.US);

    /** Search outcome given to the model. */
    public record SlotSearchResult(List<SlotOption> slots, boolean exactMatch, String note) {
    }

    /** The customer's own appointments. */
    public record MyAppointments(List<AppointmentOption> upcoming, List<AppointmentOption> recentHistory) {
    }

    /** A knowledge-base section for policy questions. */
    public record PolicyPassage(String section, String text) {
    }

    protected final AppUser customer;
    protected final Conversation conversation;
    protected final SlotService slotService;
    protected final AppointmentService appointmentService;
    protected final KnowledgeBaseService knowledgeBase;
    protected final boolean knowledgeBaseAvailable;
    protected final PendingActionStore pendingActions;
    protected final Clock clock;
    private final int maxToolCalls;
    private final List<String> toolsUsed = new CopyOnWriteArrayList<>();
    private volatile List<SlotOption> slotsFoundThisTurn = List.of();

    public BookingAgentTools(AppUser customer, Conversation conversation, SlotService slotService,
                             AppointmentService appointmentService, KnowledgeBaseService knowledgeBase,
                             boolean knowledgeBaseAvailable, PendingActionStore pendingActions, Clock clock,
                             int maxToolCalls) {
        this.customer = customer;
        this.conversation = conversation;
        this.slotService = slotService;
        this.appointmentService = appointmentService;
        this.knowledgeBase = knowledgeBase;
        this.knowledgeBaseAvailable = knowledgeBaseAvailable;
        this.pendingActions = pendingActions;
        this.clock = clock;
        this.maxToolCalls = maxToolCalls;
    }

    @Tool(description = "List the service types and repair shops (providers) that AutoBook offers.")
    public String listServicesAndProviders() {
        if (!allowCall("listServicesAndProviders")) {
            return limitMessage();
        }
        return "Services: " + String.join(", ", slotService.getServices().stream().map(ServiceDto::name).toList())
                + ". Repair shops: " + String.join(", ", slotService.getProviders().stream().map(ProviderDto::name).toList()) + ".";
    }

    @Tool(description = "Search open appointment slots, earliest first. All filters are optional. Dates use the "
            + "format yyyy-MM-dd in the shop's local time zone. Returns at most 8 slots. If no slot matches "
            + "exactly, it returns the nearest alternatives with exactMatch=false. If a service or shop name is "
            + "unknown or ambiguous it returns no slots and a note: ask the customer to clarify.")
    public SlotSearchResult searchAvailableSlots(
            @ToolParam(required = false, description = "Service name, e.g. Oil Change, Vehicle Inspection, "
                    + "Vehicle Diagnostics, Tire Service") String serviceName,
            @ToolParam(required = false, description = "Repair shop name") String providerName,
            @ToolParam(required = false, description = "First day to search, yyyy-MM-dd (default today)") String fromDate,
            @ToolParam(required = false, description = "Last day to search, yyyy-MM-dd (default 7 days after fromDate)")
            String toDate,
            @ToolParam(required = false, description = "morning (before 12:00), afternoon (12:00 to 17:00), "
                    + "or evening (17:00 and later)") String timeOfDay) {
        if (!allowCall("searchAvailableSlots")) {
            return new SlotSearchResult(List.of(), false, limitMessage());
        }

        Match<ServiceDto> service = match(serviceName, slotService.getServices(), ServiceDto::name);
        if (service.problem() != null) {
            return new SlotSearchResult(List.of(), false, service.problem() + " Services offered: "
                    + String.join(", ", slotService.getServices().stream().map(ServiceDto::name).toList())
                    + ". Ask the customer which one they mean.");
        }
        Match<ProviderDto> provider = match(providerName, slotService.getProviders(), ProviderDto::name);
        if (provider.problem() != null) {
            return new SlotSearchResult(List.of(), false, provider.problem() + " Repair shops: "
                    + String.join(", ", slotService.getProviders().stream().map(ProviderDto::name).toList())
                    + ". Ask the customer which one they mean.");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate from;
        LocalDate to;
        try {
            from = isBlank(fromDate) ? today : LocalDate.parse(fromDate.strip());
            to = isBlank(toDate) ? from.plusDays(7) : LocalDate.parse(toDate.strip());
        } catch (DateTimeParseException ex) {
            return new SlotSearchResult(List.of(), false, "Dates must use the format yyyy-MM-dd. Today is " + today + ".");
        }
        if (from.isBefore(today)) {
            from = today;
        }
        if (to.isBefore(from)) {
            return new SlotSearchResult(List.of(), false, "The end date is before the start date (today is " + today + ").");
        }
        if (to.isAfter(from.plusDays(MAX_SEARCH_DAYS))) {
            to = from.plusDays(MAX_SEARCH_DAYS);
        }
        String period = isBlank(timeOfDay) ? "" : timeOfDay.strip().toLowerCase(Locale.ROOT);
        if (!period.isEmpty() && !List.of("morning", "afternoon", "evening").contains(period)) {
            return new SlotSearchResult(List.of(), false, "timeOfDay must be morning, afternoon, or evening.");
        }

        Long serviceId = service.value() == null ? null : service.value().serviceId();
        Long providerId = provider.value() == null ? null : provider.value().providerId();
        List<SlotOption> found = search(serviceId, providerId, from, to, period, MAX_SLOTS_RETURNED);
        if (!found.isEmpty()) {
            return remember(new SlotSearchResult(found, true, found.size() + " open slot(s) found."));
        }
        // Nothing matched: suggest the nearest open slots for the same service, any shop, any time of day.
        List<SlotOption> alternatives = search(serviceId, null, today, today.plusDays(MAX_SEARCH_DAYS), "", 5);
        String note = alternatives.isEmpty()
                ? "No open slots match, and there are no alternatives in the next " + MAX_SEARCH_DAYS + " days."
                : "No open slot matched exactly. These are the nearest alternatives; offer them to the customer.";
        return remember(new SlotSearchResult(alternatives, false, note));
    }

    @Tool(description = "List the signed-in customer's own appointments: upcoming ones (with appointmentId for "
            + "cancellation) and recent history.")
    public MyAppointments listMyAppointments() {
        if (!allowCall("listMyAppointments")) {
            return new MyAppointments(List.of(), List.of());
        }
        AppointmentHistoryDto appointments = appointmentService.getAppointments(customer);
        return new MyAppointments(
                appointments.upcoming().stream().map(this::toOption).toList(),
                appointments.history().stream().limit(5).map(this::toOption).toList());
    }

    @Tool(description = "Look up AutoBook policies and FAQs (cancellation, rescheduling, business hours, services, "
            + "booking rules) in the AutoBook knowledge base. Answer policy questions only from what this returns.")
    public List<PolicyPassage> searchKnowledgeBase(
            @ToolParam(description = "The customer's policy question") String question) {
        if (!allowCall("searchKnowledgeBase")) {
            return List.of(new PolicyPassage("limit", limitMessage()));
        }
        if (!knowledgeBaseAvailable) {
            return List.of(new PolicyPassage("unavailable", "The knowledge base is not available right now."));
        }
        if (isBlank(question)) {
            return List.of();
        }
        String bounded = question.length() > 500 ? question.substring(0, 500) : question;
        List<RetrievedPassage> passages = knowledgeBase.retrieve(bounded);
        if (passages.isEmpty()) {
            return List.of(new PolicyPassage("none", "The AutoBook knowledge base does not cover this question."));
        }
        return passages.stream().map(passage -> new PolicyPassage(passage.section(), passage.text())).toList();
    }

    @Tool(description = "Prepare a booking of one open slot for the signed-in customer. This does NOT book: it "
            + "creates a confirmation request that the customer must confirm. Call it only when the customer asked "
            + "to book that specific slot. Afterwards, tell the customer what will be booked and ask them to confirm.")
    public String requestBooking(@ToolParam(description = "slotId from searchAvailableSlots") Long slotId) {
        if (!allowCall("requestBooking")) {
            return limitMessage();
        }
        if (slotId == null || slotId < 1) {
            return "A valid slotId from searchAvailableSlots is required.";
        }
        SlotDetailsDto slot;
        try {
            slot = slotService.getSlotDetails(slotId);
        } catch (ResourceNotFoundException ex) {
            return "Slot #" + slotId + " does not exist. Search again and use a slotId from the results.";
        }
        if (!slot.available() || !slot.startTime().isAfter(LocalDateTime.now(clock))) {
            List<SlotOption> alternatives = alternativesFor(slot.serviceId(), slot.slotId());
            return "Slot #" + slotId + " is no longer available, so nothing was prepared. Nearest open "
                    + slot.serviceName() + " slots: " + describe(alternatives) + " Offer these to the customer.";
        }
        String summary = "Book " + slot.serviceName() + " at " + slot.providerName() + " on "
                + DISPLAY.format(slot.startTime()) + " (slot #" + slot.slotId() + ")";
        PendingActionStore.PendingAction action = pendingActions.create(customer.userId(), conversation.conversationId(),
                PendingActionStore.Type.BOOK, slot.slotId(), summary);
        return "CONFIRMATION REQUIRED, NOT BOOKED YET: " + action.summary() + ". Ask the customer to confirm "
                + "(Confirm button or reply yes). The request expires in "
                + java.time.Duration.between(clock.instant(), action.expiresAt()).toMinutes() + " minutes.";
    }

    @Tool(description = "Prepare the cancellation of one of the signed-in customer's own upcoming appointments. This "
            + "does NOT cancel: the customer must confirm. Use appointmentId from listMyAppointments.")
    public String requestCancellation(@ToolParam(description = "appointmentId from listMyAppointments") Long appointmentId) {
        if (!allowCall("requestCancellation")) {
            return limitMessage();
        }
        if (appointmentId == null || appointmentId < 1) {
            return "A valid appointmentId from listMyAppointments is required.";
        }
        AppointmentDto appointment;
        try {
            // Ownership is enforced by the existing service: another customer's appointment is refused.
            appointment = appointmentService.getAppointment(customer, appointmentId);
        } catch (ForbiddenOperationException | ResourceNotFoundException ex) {
            return "Appointment #" + appointmentId + " is not one of your appointments, so it cannot be cancelled.";
        }
        if (appointment.status() != AppointmentStatus.BOOKED || !appointmentService.isCancellable(appointment)) {
            return "Appointment #" + appointmentId + " cannot be cancelled: it is "
                    + appointment.status().name().toLowerCase(Locale.ROOT) + " or has already started.";
        }
        String summary = "Cancel your " + appointment.serviceName() + " at " + appointment.providerName() + " on "
                + DISPLAY.format(appointment.startTime()) + " (confirmation "
                + BookingNotifications.confirmationNumber(appointment) + ")";
        PendingActionStore.PendingAction action = pendingActions.create(customer.userId(), conversation.conversationId(),
                PendingActionStore.Type.CANCEL, appointmentId, summary);
        return "CONFIRMATION REQUIRED, NOT CANCELLED YET: " + action.summary() + ". Ask the customer to confirm.";
    }

    /** Open slots for the same service, excluding one slot, for "that slot is gone" answers. */
    public List<SlotOption> alternativesFor(Long serviceId, Long excludedSlotId) {
        LocalDate today = LocalDate.now(clock);
        List<SlotOption> alternatives = search(serviceId, null, today, today.plusDays(MAX_SEARCH_DAYS), "", 6).stream()
                .filter(option -> excludedSlotId == null || option.slotId() != excludedSlotId)
                .limit(5)
                .toList();
        slotsFoundThisTurn = alternatives;
        if (!alternatives.isEmpty()) {
            conversation.offeredSlots(alternatives);
        }
        return alternatives;
    }

    static String describe(List<SlotOption> slots) {
        if (slots.isEmpty()) {
            return "none in the next " + MAX_SEARCH_DAYS + " days.";
        }
        StringBuilder text = new StringBuilder();
        for (SlotOption slot : slots) {
            text.append("slot #").append(slot.slotId()).append(" ").append(slot.provider()).append(" ")
                    .append(slot.start()).append("; ");
        }
        return text.toString().strip();
    }

    public List<String> toolsUsed() {
        return List.copyOf(toolsUsed);
    }

    public List<SlotOption> slotsFoundThisTurn() {
        return slotsFoundThisTurn;
    }

    protected boolean allowCall(String toolName) {
        if (toolsUsed.size() >= maxToolCalls) {
            return false;
        }
        toolsUsed.add(toolName);
        return true;
    }

    protected String limitMessage() {
        return "Tool call limit reached for this message. Answer with the information you already have.";
    }

    protected AppointmentOption toOption(AppointmentDto appointment) {
        return new AppointmentOption(appointment.appointmentId(), BookingNotifications.confirmationNumber(appointment),
                appointment.serviceName(), appointment.providerName(), DISPLAY.format(appointment.startTime()),
                appointment.startTime().toString(), appointment.status().name(),
                appointmentService.isCancellable(appointment));
    }

    protected static SlotOption toOption(AvailabilitySlotDto slot) {
        return new SlotOption(slot.slotId(), slot.serviceName(), slot.providerName(), DISPLAY.format(slot.startTime()),
                slot.startTime().toString(), Duration.between(slot.startTime(), slot.endTime()).toMinutes());
    }

    protected List<SlotOption> search(Long serviceId, Long providerId, LocalDate from, LocalDate to, String period,
                                      int limit) {
        List<SlotOption> result = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to) && result.size() < limit; day = day.plusDays(1)) {
            List<AvailabilitySlotDto> slots = slotService.searchAvailableSlots(
                    new SlotSearchCriteria(providerId, serviceId, day, 0, SlotSearchCriteria.MAX_PAGE_SIZE)).content();
            for (AvailabilitySlotDto slot : slots) {
                if (result.size() < limit && inPeriod(slot.startTime(), period)) {
                    result.add(toOption(slot));
                }
            }
        }
        return result;
    }

    private SlotSearchResult remember(SlotSearchResult result) {
        slotsFoundThisTurn = result.slots();
        if (!result.slots().isEmpty()) {
            conversation.offeredSlots(result.slots());
        }
        return result;
    }

    private static boolean inPeriod(LocalDateTime start, String period) {
        int hour = start.getHour();
        return switch (period) {
            case "morning" -> hour < 12;
            case "afternoon" -> hour >= 12 && hour < 17;
            case "evening" -> hour >= 17;
            default -> true;
        };
    }

    private record Match<T>(T value, String problem) {
    }

    /** Case-insensitive match by name; blank means "no filter". */
    private static <T> Match<T> match(String requested, List<T> candidates, Function<T, String> name) {
        if (isBlank(requested)) {
            return new Match<>(null, null);
        }
        String wanted = requested.strip().toLowerCase(Locale.ROOT);
        List<T> exact = candidates.stream().filter(c -> name.apply(c).equalsIgnoreCase(wanted)).toList();
        if (exact.size() == 1) {
            return new Match<>(exact.get(0), null);
        }
        List<T> partial = candidates.stream().filter(c -> {
            String candidate = name.apply(c).toLowerCase(Locale.ROOT);
            return candidate.contains(wanted) || wanted.contains(candidate);
        }).toList();
        if (partial.size() == 1) {
            return new Match<>(partial.get(0), null);
        }
        if (partial.isEmpty()) {
            return new Match<>(null, "\"" + requested.strip() + "\" was not found.");
        }
        return new Match<>(null, "\"" + requested.strip() + "\" matches more than one: "
                + String.join(", ", partial.stream().map(name).toList()) + ".");
    }

    protected static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
