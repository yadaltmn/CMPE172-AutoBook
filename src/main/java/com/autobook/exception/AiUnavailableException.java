package com.autobook.exception;

/**
 * The AI feature that was requested cannot run right now: no Gemini API key is configured, or the
 * Gemini API failed. Answered with HTTP 503. Booking, cancellation, and notifications are unaffected.
 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
