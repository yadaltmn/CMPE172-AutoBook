package com.autobook.ai;

import com.autobook.exception.AiUnavailableException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Without a Gemini API key AutoBook starts normally, creates no AI model beans, and reports the AI
 * features as unavailable instead of failing.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AiConfigurationIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AiModels aiModels;

	@Autowired
	private ApplicationContext context;

	@Test
	void applicationStartsWithoutGeminiKeyAndCreatesNoChatModel() {
		assertEquals(0, context.getBeanNamesForType(ChatModel.class).length);
		assertFalse(aiModels.chatAvailable());
		assertFalse(aiModels.embeddingAvailable());
		AiUnavailableException ex = assertThrows(AiUnavailableException.class, aiModels::chatClient);
		assertTrue(ex.getMessage().contains("no Gemini API key"), ex.getMessage());
		assertThrows(AiUnavailableException.class, aiModels::embeddingModel);
	}

	@Test
	@WithUserDetails("jada.nguyen@example.com")
	void statusEndpointReportsAiUnavailableWithoutRevealingConfiguration() throws Exception {
		mockMvc.perform(get("/api/ai/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.chatAvailable", is(false)))
				.andExpect(jsonPath("$.embeddingAvailable", is(false)))
				.andExpect(jsonPath("$.chatModel", is("")));
	}

	@Test
	void statusEndpointRequiresSignIn() throws Exception {
		mockMvc.perform(get("/api/ai/status")).andExpect(status().isUnauthorized());
	}

	@Test
	void configurationUsesGeminiDeveloperApiKeyFromEnvironmentAndNoVertexSettings() throws IOException {
		String properties = new ClassPathResource("application.properties").getContentAsString(StandardCharsets.UTF_8);

		assertTrue(properties.contains("spring.ai.google.genai.api-key=${GEMINI_API_KEY:}"));
		assertTrue(properties.contains("spring.ai.google.genai.chat.model=${GEMINI_CHAT_MODEL:"));
		assertFalse(properties.contains("spring.ai.google.genai.project-id"));
		assertFalse(properties.contains("spring.ai.google.genai.location"));
		assertFalse(properties.contains("credentials-uri"));
		// No literal key: Google AI Studio keys start with "AIza".
		assertFalse(properties.contains("AIza"));
	}
}
