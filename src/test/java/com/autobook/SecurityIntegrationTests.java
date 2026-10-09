package com.autobook;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityIntegrationTests {

	private static final String CUSTOMER = "jada.nguyen@example.com";
	private static final String PROVIDER = "service@downtownautocare.example.com";
	private static final String ADMIN = "morgan.lee@example.com";
	private static final String PASSWORD = "password123";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void customerCanLogInWithBcryptPassword() throws Exception {
		mockMvc.perform(formLogin("/login").user(CUSTOMER).password(PASSWORD))
				.andExpect(authenticated().withUsername(CUSTOMER));
	}

	@Test
	void providerCanLogIn() throws Exception {
		mockMvc.perform(formLogin("/login").user(PROVIDER).password(PASSWORD))
				.andExpect(authenticated().withUsername(PROVIDER));
	}

	@Test
	void adminCanLogIn() throws Exception {
		mockMvc.perform(formLogin("/login").user(ADMIN).password(PASSWORD))
				.andExpect(authenticated().withUsername(ADMIN));
	}

	@Test
	void incorrectPasswordIsRejected() throws Exception {
		mockMvc.perform(formLogin("/login").user(CUSTOMER).password("wrong-password"))
				.andExpect(unauthenticated())
				.andExpect(redirectedUrlPattern("/login?error*"));
	}

	@Test
	void unknownEmailIsRejected() throws Exception {
		mockMvc.perform(formLogin("/login").user("nobody@example.com").password(PASSWORD))
				.andExpect(unauthenticated());
	}

	@Test
	void unauthenticatedApiRequestReturns401Json() throws Exception {
		mockMvc.perform(get("/api/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status", is(401)))
				.andExpect(jsonPath("$.path", is("/api/me")));
	}

	@Test
	void unauthenticatedPageRequestRedirectsToLogin() throws Exception {
		mockMvc.perform(get("/customer/dashboard"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void customerRoleIsLoadedFromDatabase() throws Exception {
		mockMvc.perform(get("/api/me"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email", is(CUSTOMER)))
				.andExpect(jsonPath("$.roles", hasItem("ROLE_CUSTOMER")));
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void customerCannotUseProviderOrAdminApis() throws Exception {
		mockMvc.perform(get("/api/provider/profile"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status", is(403)));
		mockMvc.perform(get("/api/admin/providers"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithUserDetails(PROVIDER)
	void providerSeesOnlyTheirOwnProviderProfile() throws Exception {
		mockMvc.perform(get("/api/provider/profile"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name", is("Downtown Auto Care")))
				.andExpect(jsonPath("$.email", is(PROVIDER)));
	}

	@Test
	@WithUserDetails(PROVIDER)
	void providerCannotUseCustomerOrAdminApis() throws Exception {
		mockMvc.perform(get("/api/appointments/me"))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/admin/providers"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithUserDetails(ADMIN)
	void adminCanListAndUpdateProviders() throws Exception {
		mockMvc.perform(get("/api/admin/providers"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)));

		mockMvc.perform(put("/api/admin/providers/1").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Downtown Auto Care & Detailing\",\"phone\":\"408-555-0199\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name", is("Downtown Auto Care & Detailing")))
				.andExpect(jsonPath("$.phone", is("408-555-0199")));
	}

	@Test
	@WithUserDetails(ADMIN)
	void adminProviderUpdateValidatesInput() throws Exception {
		mockMvc.perform(put("/api/admin/providers/1").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"\",\"phone\":\"408-555-0199\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status", is(400)));

		mockMvc.perform(put("/api/admin/providers/999").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Ghost Garage\",\"phone\":\"\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithUserDetails(ADMIN)
	void adminCannotUseCustomerOrProviderApis() throws Exception {
		mockMvc.perform(get("/api/appointments/me"))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/provider/profile"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithUserDetails(ADMIN)
	void stateChangingRequestWithoutCsrfTokenIsForbidden() throws Exception {
		mockMvc.perform(put("/api/admin/providers/1")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"No Token Garage\",\"phone\":\"\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void csrfTokenEndpointReturnsToken() throws Exception {
		mockMvc.perform(get("/api/csrf"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.headerName", is("X-CSRF-TOKEN")));
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void logoutEndsTheSession() throws Exception {
		mockMvc.perform(logout())
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("/login?logout*"))
				.andExpect(unauthenticated());
	}
}
