package dev.sereda.brandlift.campaign;

import java.util.List;

/**
 * Simple error body. {@code message} is a short summary; {@code details} carries
 * per-field validation messages when relevant (empty otherwise).
 */
public record ApiError(String message, List<String> details) {
}
