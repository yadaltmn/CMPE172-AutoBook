package com.autobook.model;

public record AppUser(
        Long userId,
        String firstName,
        String lastName,
        String email,
        UserRole role
) {

    public String fullName() {
        return firstName + " " + lastName;
    }
}
