package com.autobook.ai.chat;

import com.autobook.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/ai/chat: the conversational booking assistant (customers only, CSRF protected).
 * The customer is resolved from the authenticated session, never from the request body.
 */
@RestController
public class AiChatController {

    private final ConversationalAgentService agent;
    private final BookingConfirmationService confirmations;
    private final CurrentUserService currentUserService;

    public AiChatController(ConversationalAgentService agent, BookingConfirmationService confirmations,
                            CurrentUserService currentUserService) {
        this.agent = agent;
        this.confirmations = confirmations;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/api/ai/chat")
    public ChatReply chat(@Valid @RequestBody ChatRequest request, Authentication authentication) {
        return agent.chat(currentUserService.requireUser(authentication), request.conversationId(), request.message());
    }

    /**
     * POST /api/ai/chat/confirm: the customer's explicit confirmation (or refusal) of a pending booking or
     * cancellation. 200 when done or declined, 409 when the slot was taken meanwhile (with alternatives),
     * 403 for another customer's token, 400 for an expired, used, or unknown token.
     */
    @PostMapping("/api/ai/chat/confirm")
    public ResponseEntity<ChatReply> confirm(@Valid @RequestBody ConfirmationRequest request,
                                             Authentication authentication) {
        var customer = currentUserService.requireUser(authentication);
        BookingConfirmationService.Outcome outcome = Boolean.FALSE.equals(request.confirm())
                ? confirmations.decline(customer, request.conversationId(), request.token())
                : confirmations.confirm(customer, request.conversationId(), request.token());
        HttpStatus status = outcome.status() == BookingConfirmationService.Status.CONFLICT ? HttpStatus.CONFLICT : HttpStatus.OK;
        return ResponseEntity.status(status).body(outcome.reply());
    }
}
