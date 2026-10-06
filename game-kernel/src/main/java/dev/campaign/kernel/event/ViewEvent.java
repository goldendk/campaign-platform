package dev.campaign.kernel.event;

import dev.campaign.kernel.api.Event;

/** An event as one particular viewer is allowed to see it (full or redacted). */
public record ViewEvent(long seq, int turn, String phase, Event event) {}
