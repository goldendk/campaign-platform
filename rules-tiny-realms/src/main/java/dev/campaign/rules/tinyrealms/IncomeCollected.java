package dev.campaign.rules.tinyrealms;

import dev.campaign.kernel.api.Event;
import dev.campaign.kernel.id.FactionId;

/**
 * A ruleset-specific event, to show the extension point: it is registered together with a reducer
 * (see TinyRealms.register), so replay applies it like any core event.
 */
public record IncomeCollected(FactionId faction, long gold, long locations) implements Event {}
