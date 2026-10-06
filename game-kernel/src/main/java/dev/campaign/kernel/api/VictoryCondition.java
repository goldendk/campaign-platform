package dev.campaign.kernel.api;

import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.state.StateReader;

import java.util.Optional;

@FunctionalInterface
public interface VictoryCondition {
    Optional<FactionId> check(StateReader state);
}
