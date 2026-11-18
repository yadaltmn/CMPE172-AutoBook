package com.autobook.ai.rag;

import com.autobook.ai.AiTestModels;
import com.autobook.ai.KeywordEmbeddingModel;
import com.autobook.ai.ScriptedChatModel;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Retrieval-augmented FAQ answering: ingestion into the SimpleVectorStore, retrieval of the relevant
 * sections, grounding of the prompt in those sections only, and refusal for unsupported questions.
 * Uses the keyword embedding model and the scripted chat model instead of Gemini.
 */
@SpringBootTest(properties = "autobook.ai.faq.similarity-threshold=0.15")
@AutoConfigureMockMvc
@Import(AiTestModels.class)
class RagIntegrationTests {

	private static final String CUSTOMER = "jada.nguyen@example.com";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private KnowledgeBaseService knowledgeBase;

	@Autowired
	private ScriptedChatModel chatModel;

	@Autowired
	private KeywordEmbeddingModel embeddingModel;

	@BeforeEach
	void resetModel() {
		chatModel.reset();
	}

	@Test
	void everyKnowledgeChunkIsEmbeddedIntoTheVectorStore() {
		knowledgeBase.retrieve("cancel appointment");

		assertTrue(knowledgeBase.isIndexed());
		List<String> embedded = embeddingModel.embeddedTexts();
		for (KnowledgeChunk chunk : knowledgeBase.chunks()) {
			assertTrue(embedded.contains(chunk.text()), "embedded: " + chunk.section());
		}
	}

	@Test
	void retrievalReturnsTheRelevantSectionFirst() {
		List<RetrievedPassage> cancellation = knowledgeBase.retrieve("How do I cancel my appointment?");
		List<RetrievedPassage> hours = knowledgeBase.retrieve("What are your business hours on Saturday?");
		List<RetrievedPassage> tires = knowledgeBase.retrieve("Do you offer tire rotation?");

		assertEquals("Cancellation policy", cancellation.get(0).section());
		assertEquals("Business hours", hours.get(0).section());
		assertEquals("Services offered", tires.get(0).section());
		assertTrue(cancellation.size() <= 4, "at most top-k results");
		assertTrue(cancellation.get(0).score() >= 0.15);
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void answerIsGeneratedFromRetrievedSectionsOnlyAndCitesThem() throws Exception {
		chatModel.script((prompt, tools) -> "You can cancel any time before the appointment starts [1].");

		mockMvc.perform(post("/api/ai/faq").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"What is the cancellation policy?\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.answer", containsString("[1]")))
				.andExpect(jsonPath("$.retrievedFromKnowledgeBase", is(true)))
				.andExpect(jsonPath("$.sources[0].ref", is("[1]")))
				.andExpect(jsonPath("$.sources[0].section", is("Cancellation policy")));

		String prompt = ScriptedChatModel.text(chatModel.lastPrompt());
		assertTrue(prompt.contains("Answer the user's question using ONLY the knowledge-base excerpts"));
		assertTrue(prompt.contains("at any time before the appointment"), "the retrieved policy text is in the prompt");
		assertTrue(prompt.contains("<excerpt id=\"1\" section=\"Cancellation policy\">"));
		// Not the whole knowledge file: an unrelated section is not sent.
		assertFalse(prompt.contains("bring your vehicle registration"), "unrelated section was sent to the model");
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void unsupportedQuestionIsRefusedWithoutCallingTheModel() throws Exception {
		mockMvc.perform(post("/api/ai/faq").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"What is the capital of France?\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.retrievedFromKnowledgeBase", is(false)))
				.andExpect(jsonPath("$.answer", containsString("couldn't find that in the AutoBook knowledge base")))
				.andExpect(jsonPath("$.sources.length()", is(0)));

		assertTrue(chatModel.prompts().isEmpty(), "no model call, so no invented answer");
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void modelFailureIsReportedAsUnavailableNotAsAnAnswer() throws Exception {
		chatModel.failWith(new IllegalStateException("simulated Gemini outage"));

		mockMvc.perform(post("/api/ai/faq").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"How do I book an appointment?\"}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.message", containsString("temporarily unavailable")));
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void questionIsValidated() throws Exception {
		mockMvc.perform(post("/api/ai/faq").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"  \"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/ai/faq").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"" + "a".repeat(501) + "\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void faqRequiresSignIn() throws Exception {
		mockMvc.perform(post("/api/ai/faq").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Hours?\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void faqRequiresCsrfToken() throws Exception {
		mockMvc.perform(post("/api/ai/faq")
						.contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Hours?\"}"))
				.andExpect(status().isForbidden());
	}
}
