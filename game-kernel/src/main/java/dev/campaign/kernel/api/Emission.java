package dev.campaign.kernel.api;

import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.model.Audience;

/**
 * An event plus who may see it. If the audience does not include a viewer, that viewer receives {@code redacted}
 * instead (or nothing, when it is null).
 */
public record Emission(Audience audience, Event event, Event redacted) {
    public static Emission publicly(Event event) {
        return new Emission(Audience.all(), event, null);
    }

    public static Emission toFaction(FactionId faction, Event event, Event redactedForOthers) {
        return new Emission(Audience.only(faction), event, redactedForOthers);
    }

    public static Emission gmOnly(Event event) {
        return new Emission(Audience.gmOnly(), event, null);
    }
}
