package dev.campaign.kernel.api;

import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.model.Entity;
import dev.campaign.kernel.state.StateReader;

/** Fog-of-war seam: may this faction see this entity? The default shows everything. */
@FunctionalInterface
public interface ViewPolicy {
    boolean canSee(StateReader state, FactionId viewer, Entity entity);

    static ViewPolicy everythingVisible() {
        return (state, viewer, entity) -> true;
    }
}
