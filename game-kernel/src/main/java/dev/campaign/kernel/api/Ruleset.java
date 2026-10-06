package dev.campaign.kernel.api;

import java.util.List;

/**
 * A setting-specific rule module: Warhammer campaign, space trading, whatever. Implementations must be plain Java
 * (no Spring). Register through META-INF/services/dev.campaign.kernel.api.Ruleset so game-app can discover them.
 */
public interface Ruleset {
    /** Stable id, also used as the JSON type-name prefix for this ruleset's commands and events. */
    String id();

    /** Declare phases, commands, event types, hooks and victory conditions. */
    void register(RulesetRegistry registry);

    /** Initial world: locations, entities, starting resources. Emitted publicly before turn 1. */
    List<Emission> setup(CampaignConfig config);
}
