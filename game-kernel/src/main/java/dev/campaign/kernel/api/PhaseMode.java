package dev.campaign.kernel.api;

/** How players act within an ORDERS phase. Selected per phase by configuration. */
public enum PhaseMode {
    /** Everyone stages orders in secret; the phase ends when all have locked in (or the deadline passes). */
    SIMULTANEOUS,
    /** Players act one after another. Declared, selectable in config, not implemented yet. */
    SEQUENTIAL
}
