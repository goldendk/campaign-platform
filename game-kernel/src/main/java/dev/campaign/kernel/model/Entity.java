package dev.campaign.kernel.model;

import dev.campaign.kernel.id.EntityId;
import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.id.LocationId;
import dev.campaign.kernel.util.Sorted;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Anything that sits on the board: an army, a cargo ship, a hero, a caravan. The kernel does not know what a
 * "kind" means; rulesets give meaning through tags ("mobile", "unit") and numeric stats ("move", "strength").
 */
public record Entity(EntityId id, String kind, FactionId owner, LocationId at, Set<String> tags, Map<String, Long> stats) {
    public Entity {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        tags = Sorted.set(tags);
        stats = Sorted.map(stats);
    }

    public boolean has(String tag) {
        return tags.contains(tag);
    }

    public long stat(String name, long fallback) {
        return stats.getOrDefault(name, fallback);
    }

    public Entity movedTo(LocationId destination) {
        return new Entity(id, kind, owner, destination, tags, stats);
    }
}
