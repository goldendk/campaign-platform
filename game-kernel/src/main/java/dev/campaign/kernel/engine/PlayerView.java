package dev.campaign.kernel.engine;

import dev.campaign.kernel.api.Command;
import dev.campaign.kernel.id.EntityId;
import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.id.LocationId;
import dev.campaign.kernel.model.Entity;
import dev.campaign.kernel.model.Location;
import dev.campaign.kernel.model.PendingItem;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The state as one viewer may see it. This is the read model returned by the API: the same records, filtered,
 * with no mapping layer in between.
 */
public record PlayerView(
        long version,
        int turn,
        String phase,
        Instant deadline,
        boolean ended,
        FactionId winner,
        List<Location> locations,
        Map<LocationId, Set<LocationId>> adjacency,
        List<Entity> entities,
        List<FactionView> factions,
        Map<FactionId, List<Command>> visibleOrders,
        Set<FactionId> lockedIn,
        List<PendingItem> pending) {

    public FactionView faction(FactionId id) {
        return factions.stream().filter(f -> f.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such faction in view: " + id));
    }

    public Optional<Entity> entity(EntityId id) {
        return entities.stream().filter(e -> e.id().equals(id)).findFirst();
    }

    public Location location(LocationId id) {
        return locations.stream().filter(l -> l.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such location in view: " + id));
    }

    public List<Entity> entitiesOf(FactionId faction) {
        return entities.stream().filter(e -> faction.equals(e.owner())).toList();
    }
}
