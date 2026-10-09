package com.autobook.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        RequestMatcher apiRequests = PathPatternRequestMatcher.withDefaults().matcher("/api/**");

        http.authorizeHttpRequests(auth -> auth
                // Public pages, static assets, and the Milestone 1 JSON endpoints.
                .requestMatchers("/", "/slots", "/login", "/browse", "/error", "/access-denied",
                        "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/slots", "/api/providers", "/api/services", "/api/csrf").permitAll()
                // Role-restricted areas. Ownership of individual records is enforced in the service layer.
                .requestMatchers("/customer/**", "/api/appointments/**").hasRole("CUSTOMER")
                .requestMatchers("/provider/**", "/api/provider/**").hasRole("PROVIDER")
                .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
        );

        http.formLogin(form -> form.permitAll());

        http.logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
        );

        // Browser pages keep the default redirect-to-login behaviour; JSON API clients get 401/403 bodies instead.
        http.exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                        (request, response, ex) -> writeJsonError(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                                "Unauthorized", "Authentication is required."),
                        apiRequests)
                .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"), AnyRequestMatcher.INSTANCE)
                .defaultAccessDeniedHandlerFor(
                        (request, response, ex) -> writeJsonError(request, response, HttpServletResponse.SC_FORBIDDEN,
                                "Forbidden", "You do not have permission to access this resource."),
                        apiRequests)
                .defaultAccessDeniedHandlerFor(browserAccessDeniedHandler(), AnyRequestMatcher.INSTANCE)
        );

        return http.build();
    }

    private static AccessDeniedHandlerImpl browserAccessDeniedHandler() {
        AccessDeniedHandlerImpl handler = new AccessDeniedHandlerImpl();
        handler.setErrorPage("/access-denied");
        return handler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void writeJsonError(HttpServletRequest request, HttpServletResponse response,
                                       int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"timestamp\":\"" + LocalDateTime.now()
                + "\",\"status\":" + status
                + ",\"error\":\"" + error
                + "\",\"message\":\"" + message
                + "\",\"path\":\"" + request.getRequestURI().replace("\"", "") + "\"}");
    }
}
