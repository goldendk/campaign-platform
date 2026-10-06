package dev.campaign.kernel.engine;

import dev.campaign.kernel.api.PhaseMode;
import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.model.Faction;
import dev.campaign.kernel.state.StateReader;

/**
 * How an ORDERS phase behaves for a given PhaseMode. Adding "take turns" means adding one implementation here
 * and one case in forMode; nothing else in the kernel changes.
 */
public interface PhaseStrategy {
    /** May this faction stage (or revise) orders right now? */
    boolean mayStage(StateReader state, FactionId faction);

    /** Is the phase over and ready to resolve? */
    boolean isComplete(StateReader state);

    static PhaseStrategy forMode(PhaseMode mode) {
        return switch (mode) {
            case SIMULTANEOUS -> new Simultaneous();
            case SEQUENTIAL -> throw new UnsupportedOperationException("SEQUENTIAL phases are not implemented yet");
        };
    }

    final class Simultaneous implements PhaseStrategy {
        @Override
        public boolean mayStage(StateReader state, FactionId faction) {
            return !state.lockedIn().contains(faction);
        }

        @Override
        public boolean isComplete(StateReader state) {
            if (state.factions().isEmpty()) return false;
            for (Faction f : state.factions()) if (!state.lockedIn().contains(f.id())) return false;
            return true;
        }
    }
}
