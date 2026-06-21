package dev.sereda.brandlift.simulation;

/**
 * Advertising channels an exposure event can come from. Kept as a small, fixed
 * enum because the set is stable and we want exhaustive, type-safe handling later.
 */
public enum Channel {
    CTV,
    SOCIAL,
    DISPLAY,
    STREAMING_AUDIO,
    RETAIL_MEDIA,
    LINEAR_TV
}
