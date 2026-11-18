package com.autobook.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The assistant page is part of the customer website and carries the CSRF token the script needs.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AssistantPageIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@WithUserDetails("jada.nguyen@example.com")
	void customerSeesTheAssistantPage() throws Exception {
		mockMvc.perform(get("/customer/assistant"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("AutoBook assistant")))
				.andExpect(content().string(containsString("data-csrf-token=")))
				.andExpect(content().string(containsString("/js/assistant.js")))
				.andExpect(content().string(containsString("href=\"/customer/assistant\"")));
	}

	@Test
	@WithUserDetails("service@downtownautocare.example.com")
	void providersCannotOpenTheCustomerAssistant() throws Exception {
		mockMvc.perform(get("/customer/assistant")).andExpect(status().isForbidden());
	}
}
