package com.autobook.ai.chat;

import com.autobook.service.CurrentUserService;
import jakarta.validation.Valid;
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
    private final CurrentUserService currentUserService;

    public AiChatController(ConversationalAgentService agent, CurrentUserService currentUserService) {
        this.agent = agent;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/api/ai/chat")
    public ChatReply chat(@Valid @RequestBody ChatRequest request, Authentication authentication) {
        return agent.chat(currentUserService.requireUser(authentication), request.conversationId(), request.message());
    }
}
