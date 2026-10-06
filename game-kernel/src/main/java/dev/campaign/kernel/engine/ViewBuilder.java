package dev.campaign.kernel.engine;

import dev.campaign.kernel.api.Command;
import dev.campaign.kernel.api.ViewPolicy;
import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.id.LocationId;
import dev.campaign.kernel.model.Actor;
import dev.campaign.kernel.model.Entity;
import dev.campaign.kernel.model.Faction;
import dev.campaign.kernel.model.Location;
import dev.campaign.kernel.state.StateReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Projection of the full state onto what one viewer may see. The GM sees everything. */
final class ViewBuilder {
    private ViewBuilder() {}

    static PlayerView build(StateReader s, Actor viewer, ViewPolicy policy) {
        boolean gm = viewer.isGm();
        FactionId own = gm ? null : s.factionOf(viewer.player()).orElse(null);

        List<Location> locations = List.copyOf(s.locations());
        Map<LocationId, Set<LocationId>> adjacency = new TreeMap<>();
        for (Location l : locations) adjacency.put(l.id(), s.neighbours(l.id()));

        List<Entity> entities = s.entities().stream().filter(e -> gm || policy.canSee(s, own, e)).toList();

        List<FactionView> factions = new ArrayList<>();
        Map<FactionId, List<Command>> orders = new TreeMap<>();
        for (Faction f : s.factions()) {
            boolean full = gm || f.id().equals(own);
            factions.add(new FactionView(f.id(), f.name(), f.player(), full ? f.resources() : null));
            if (full) {
                List<Command> staged = s.stagedOrders(f.id());
                if (!staged.isEmpty()) orders.put(f.id(), staged);
            }
        }

        return new PlayerView(s.version(), s.turn(), s.phase(), s.deadline(), s.ended(), s.winner(),
                locations, adjacency, entities, factions, orders, Set.copyOf(s.lockedIn()), s.openPending());
    }
}
