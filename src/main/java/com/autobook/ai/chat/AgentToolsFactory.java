package com.autobook.ai.chat;

import com.autobook.ai.AiModels;
import com.autobook.ai.AiProperties;
import com.autobook.ai.rag.KnowledgeBaseService;
import com.autobook.model.AppUser;
import com.autobook.service.AppointmentService;
import com.autobook.service.SlotService;
import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * Creates the per-message {@link BookingAgentTools} session bound to one authenticated customer.
 */
@Component
public class AgentToolsFactory {

    private final SlotService slotService;
    private final AppointmentService appointmentService;
    private final KnowledgeBaseService knowledgeBase;
    private final AiModels aiModels;
    private final PendingActionStore pendingActions;
    private final AiProperties properties;
    private final Clock clock;

    public AgentToolsFactory(SlotService slotService, AppointmentService appointmentService,
                             KnowledgeBaseService knowledgeBase, AiModels aiModels, PendingActionStore pendingActions,
                             AiProperties properties, Clock clock) {
        this.slotService = slotService;
        this.appointmentService = appointmentService;
        this.knowledgeBase = knowledgeBase;
        this.aiModels = aiModels;
        this.pendingActions = pendingActions;
        this.properties = properties;
        this.clock = clock;
    }

    public BookingAgentTools create(AppUser customer, Conversation conversation) {
        return new BookingAgentTools(customer, conversation, slotService, appointmentService, knowledgeBase,
                aiModels.embeddingAvailable(), pendingActions, clock, properties.maxToolCallsPerMessage());
    }
}
