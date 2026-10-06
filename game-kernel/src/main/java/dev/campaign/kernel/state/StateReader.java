package dev.campaign.kernel.state;

import dev.campaign.kernel.api.Command;
import dev.campaign.kernel.api.PhaseMode;
import dev.campaign.kernel.id.EntityId;
import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.id.LocationId;
import dev.campaign.kernel.id.PlayerId;
import dev.campaign.kernel.model.Entity;
import dev.campaign.kernel.model.Faction;
import dev.campaign.kernel.model.Location;
import dev.campaign.kernel.model.PendingItem;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Read-only view of the full game state, as seen by handlers, hooks and victory conditions. */
public interface StateReader {
    String rulesetId();
    long seed();
    /** Sequence number of the last applied event. */
    long version();
    int turn();
    String phase();
    /** Deadline of the current phase, or null. */
    Instant deadline();
    boolean ended();
    FactionId winner();
    long entitySerial();
    PhaseMode phaseMode(String phaseId, PhaseMode declared);

    Optional<Location> location(LocationId id);
    Collection<Location> locations();
    Set<LocationId> neighbours(LocationId id);

    Optional<Entity> entity(EntityId id);
    Collection<Entity> entities();
    List<Entity> entitiesAt(LocationId location);
    List<Entity> entitiesOf(FactionId faction);

    Optional<Faction> faction(FactionId id);
    Collection<Faction> factions();
    Optional<FactionId> factionOf(PlayerId player);
    long resource(FactionId faction, String resource);

    Optional<PendingItem> pending(String id);
    List<PendingItem> openPending();

    Set<FactionId> lockedIn();
    List<Command> stagedOrders(FactionId faction);
}
