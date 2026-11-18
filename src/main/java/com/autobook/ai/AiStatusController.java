package com.autobook.ai;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets the website (and a signed-in user) check whether the AI features are switched on.
 * Never returns the API key.
 */
@RestController
public class AiStatusController {

    private final AiModels aiModels;

    public AiStatusController(AiModels aiModels) {
        this.aiModels = aiModels;
    }

    @GetMapping("/api/ai/status")
    public Map<String, Object> status() {
        return Map.of(
                "chatAvailable", aiModels.chatAvailable(),
                "embeddingAvailable", aiModels.embeddingAvailable(),
                "chatModel", aiModels.chatAvailable() ? aiModels.chatModelName() : "",
                "embeddingModel", aiModels.embeddingAvailable() ? aiModels.embeddingModelName() : "");
    }
}
