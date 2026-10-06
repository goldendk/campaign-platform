package dev.campaign.kernel.event;

import dev.campaign.kernel.api.Event;
import dev.campaign.kernel.model.Audience;

import java.time.Instant;

/**
 * The persisted record. This single shape is what the store writes, what replay reads and what game-json
 * serialises: there is no separate persistence model. {@code redacted} is what everyone outside the audience sees.
 */
public record StoredEvent(long seq, Instant at, int turn, String phase, Audience audience, Event event, Event redacted) {}
