package com.autobook.ai.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real knowledge source is split into one chunk per section.
 */
class KnowledgeSourceChunkingTests {

	@Test
	void knowledgeSourceIsSplitIntoOneChunkPerSection() throws IOException {
		String markdown = new ClassPathResource("knowledge/autobook-faq.md").getContentAsString(StandardCharsets.UTF_8);
		long sections = markdown.lines().filter(line -> line.startsWith("## ")).count();

		List<KnowledgeChunk> chunks = KnowledgeBaseService.split(markdown, "autobook-faq.md");

		// Every "## " section plus the introduction.
		assertEquals(sections + 1, chunks.size());
		assertTrue(sections >= 10, "knowledge base has " + sections + " sections");
		assertEquals(chunks.size(), new HashSet<>(chunks.stream().map(KnowledgeChunk::id).toList()).size(), "ids are unique");
		for (KnowledgeChunk chunk : chunks) {
			assertFalse(chunk.text().isBlank());
			assertTrue(chunk.text().startsWith(chunk.section()), "chunk text starts with its heading");
			assertTrue(chunk.id().startsWith("autobook-faq.md#"));
		}
		assertTrue(chunks.stream().anyMatch(chunk -> chunk.section().equals("Cancellation policy")));
		assertTrue(chunks.stream().anyMatch(chunk -> chunk.section().equals("Business hours")));
	}

	@Test
	void demonstrationPoliciesAreLabelled() throws IOException {
		String markdown = new ClassPathResource("knowledge/autobook-faq.md").getContentAsString(StandardCharsets.UTF_8);
		KnowledgeChunk hours = KnowledgeBaseService.split(markdown, "autobook-faq.md").stream()
				.filter(chunk -> chunk.section().equals("Business hours")).findFirst().orElseThrow();

		assertTrue(hours.text().contains("Demonstration policy"));
	}

	@Test
	void splitKeepsSectionsSeparate() {
		List<KnowledgeChunk> chunks = KnowledgeBaseService.split("""
				# Title
				Intro text.
				## First
				Alpha.
				## Second
				Beta.
				""", "test.md");

		assertEquals(List.of("Introduction", "First", "Second"), chunks.stream().map(KnowledgeChunk::section).toList());
		assertEquals("First\nAlpha.", chunks.get(1).text());
		assertEquals("test.md#second", chunks.get(2).id());
	}
}
