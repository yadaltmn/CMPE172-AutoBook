package com.autobook.controller.web;

import com.autobook.exception.BookingConflictException;
import com.autobook.exception.ForbiddenOperationException;
import com.autobook.exception.InvalidRequestException;
import com.autobook.exception.ResourceNotFoundException;
import com.autobook.model.AppUser;
import com.autobook.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Shared model data and error pages for the Thymeleaf controllers. Error pages show a friendly
 * message only; SQL and stack traces are logged on the server and never rendered.
 */
@ControllerAdvice(basePackageClasses = WebModelAdvice.class)
public class WebModelAdvice {

    private static final Logger log = LoggerFactory.getLogger(WebModelAdvice.class);

    private final UserRepository userRepository;

    public WebModelAdvice(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @ModelAttribute("currentUser")
    public AppUser currentUser(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return userRepository.findByEmail(authentication.getName()).orElse(null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public String notFound(ResourceNotFoundException ex, Model model, HttpServletResponse response) {
        return errorPage(HttpStatus.NOT_FOUND, "We couldn't find that page", ex.getMessage(), model, response);
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public String forbidden(ForbiddenOperationException ex, Model model, HttpServletResponse response) {
        return errorPage(HttpStatus.FORBIDDEN, "Access denied", ex.getMessage(), model, response);
    }

    @ExceptionHandler({InvalidRequestException.class, BookingConflictException.class})
    public String badRequest(RuntimeException ex, Model model, HttpServletResponse response) {
        HttpStatus status = ex instanceof BookingConflictException ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return errorPage(status, "We couldn't complete that request", ex.getMessage(), model, response);
    }

    @ExceptionHandler(Exception.class)
    public String unexpected(Exception ex, Model model, HttpServletResponse response) throws Exception {
        if (ex instanceof org.springframework.security.access.AccessDeniedException
                || ex instanceof org.springframework.security.core.AuthenticationException) {
            throw ex;
        }
        log.error("Unexpected error while rendering a page", ex);
        return errorPage(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong",
                "An unexpected error occurred. Please try again in a moment.", model, response);
    }

    private String errorPage(HttpStatus status, String title, String message, Model model, HttpServletResponse response) {
        response.setStatus(status.value());
        model.addAttribute("status", status.value());
        model.addAttribute("title", title);
        model.addAttribute("message", message);
        return "error";
    }
}
