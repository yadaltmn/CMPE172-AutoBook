package com.autobook.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * When a chat model and an embedding model exist (here the test doubles, in production the Gemini
 * models), AutoBook's ChatClient sends prompts to that model.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AiTestModels.class)
class AiModelsWithTestModelIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AiModels aiModels;

	@Autowired
	private ScriptedChatModel chatModel;

	@Test
	void chatClientSendsPromptsToTheConfiguredModel() {
		chatModel.reset();
		chatModel.script((prompt, tools) -> "Echo: " + prompt.getUserMessage().getText());

		String reply = aiModels.chatClient().prompt().user("Hello AutoBook").call().content();

		assertEquals("Echo: Hello AutoBook", reply);
		assertEquals(1, chatModel.prompts().size());
		assertTrue(aiModels.embeddingAvailable());
	}

	@Test
	@WithUserDetails("jada.nguyen@example.com")
	void statusEndpointReportsAiAvailable() throws Exception {
		mockMvc.perform(get("/api/ai/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.chatAvailable", is(true)))
				.andExpect(jsonPath("$.embeddingAvailable", is(true)));
	}
}
