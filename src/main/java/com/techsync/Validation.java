package com.techsync;

import java.util.List;

public class Validation {

    private Validation() {
    }
    
    public static boolean isValidStatus(String status) {
        return status != null && List.of("OPEN", "IN_PROGRESS", "COMPLETED")
            .contains(status.toUpperCase());
    }
    
    public static boolean isValidPriority(String priority) {
        return priority != null && List.of("LOW", "MEDIUM", "HIGH")
            .contains(priority.toUpperCase());
    }
    
    public static String sanitize(String input) {
        if (input == null) return "";
        return input.trim().replaceAll("[<>\"'&]", ""); // Basic XSS prevention
    }
}
