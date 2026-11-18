package com.autobook.ai.reminder;

/** Who wrote a reminder's text. */
public enum ReminderContentSource {
    /** Written by Gemini through Spring AI. */
    GEMINI,
    /** AutoBook's fixed template, used when Gemini is not configured or failed close to the appointment. */
    TEMPLATE
}
