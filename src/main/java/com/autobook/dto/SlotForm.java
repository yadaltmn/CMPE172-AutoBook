package com.autobook.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Backing object for the provider "add availability" form. It uses separate date and time
 * fields to match the browser's date and time inputs, then converts to a {@link CreateSlotRequest}.
 */
public class SlotForm {

    @NotNull(message = "Please choose a service.")
    @Positive(message = "Please choose a service.")
    private Long serviceId;

    @NotNull(message = "Please choose a date.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;

    @NotNull(message = "Please choose a start time.")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @NotNull(message = "Please choose an end time.")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime endTime;

    public CreateSlotRequest toRequest() {
        return new CreateSlotRequest(serviceId, date.atTime(startTime), date.atTime(endTime));
    }

    public Long getServiceId() {
        return serviceId;
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }
}
