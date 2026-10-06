package dev.campaign.kernel.api;

public enum PhaseKind {
    /** Players stage orders; the kernel resolves them when the phase completes. */
    ORDERS,
    /** Waits until every pending item (e.g. tabletop battle) is resolved. */
    PENDING,
    /** Runs its entry hooks and moves on immediately (income, upkeep, growth). */
    AUTOMATIC
}
