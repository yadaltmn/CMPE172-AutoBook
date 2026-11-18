package com.autobook.ai.rag;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/ai/faq: grounded answers from the AutoBook knowledge base (any signed-in user).
 */
@RestController
public class FaqController {

    private final RagQuestionAnswerService answerService;

    public FaqController(RagQuestionAnswerService answerService) {
        this.answerService = answerService;
    }

    @PostMapping("/api/ai/faq")
    public FaqAnswer ask(@Valid @RequestBody FaqRequest request) {
        return answerService.answer(request.question());
    }
}
