package dev.campaign.kernel.model;

import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.id.LocationId;
import dev.campaign.kernel.util.Sorted;

import java.util.Objects;
import java.util.Set;

/** A node of the world graph (a hex tile, a solar system, a province). Edges live in the game state. */
public record Location(LocationId id, String kind, Set<String> tags, FactionId owner) {
    public Location {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        tags = Sorted.set(tags);
    }

    public Location withOwner(FactionId newOwner) {
        return new Location(id, kind, tags, newOwner);
    }
}
