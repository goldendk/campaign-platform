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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.UnaryOperator;

/**
 * The folded result of all events. The mutators are public only because ruleset reducers (for ruleset-defined
 * events) need them; handlers and hooks only ever see {@link StateReader}. Nothing but event application should
 * call a mutator.
 *
 * All internal collections are sorted so iteration order never depends on the JVM.
 */
public final class GameState implements StateReader {
    private String rulesetId;
    private long seed;
    private long version;
    private int turn;
    private String phase;
    private Instant deadline;
    private boolean ended;
    private FactionId winner;
    private long entitySerial;
    private Map<String, PhaseMode> phaseModes = Map.of();

    private final TreeMap<LocationId, Location> locations = new TreeMap<>();
    private final TreeMap<LocationId, TreeSet<LocationId>> adjacency = new TreeMap<>();
    private final TreeMap<EntityId, Entity> entities = new TreeMap<>();
    private final TreeMap<FactionId, Faction> factions = new TreeMap<>();
    private final TreeMap<PlayerId, FactionId> playerFaction = new TreeMap<>();
    private final TreeMap<String, PendingItem> pending = new TreeMap<>();
    private final TreeSet<FactionId> lockedIn = new TreeSet<>();
    private final TreeMap<FactionId, List<Command>> orders = new TreeMap<>();

    // ---- reads ----

    @Override public String rulesetId() { return rulesetId; }
    @Override public long seed() { return seed; }
    @Override public long version() { return version; }
    @Override public int turn() { return turn; }
    @Override public String phase() { return phase; }
    @Override public Instant deadline() { return deadline; }
    @Override public boolean ended() { return ended; }
    @Override public FactionId winner() { return winner; }
    @Override public long entitySerial() { return entitySerial; }

    @Override
    public PhaseMode phaseMode(String phaseId, PhaseMode declared) {
        return phaseModes.getOrDefault(phaseId, declared);
    }

    @Override public Optional<Location> location(LocationId id) { return Optional.ofNullable(locations.get(id)); }
    @Override public Collection<Location> locations() { return List.copyOf(locations.values()); }

    @Override
    public Set<LocationId> neighbours(LocationId id) {
        TreeSet<LocationId> n = adjacency.get(id);
        return n == null ? Set.of() : Collections.unmodifiableSortedSet(n);
    }

    @Override public Optional<Entity> entity(EntityId id) { return Optional.ofNullable(entities.get(id)); }
    @Override public Collection<Entity> entities() { return List.copyOf(entities.values()); }

    @Override
    public List<Entity> entitiesAt(LocationId location) {
        return entities.values().stream().filter(e -> location.equals(e.at())).toList();
    }

    @Override
    public List<Entity> entitiesOf(FactionId faction) {
        return entities.values().stream().filter(e -> faction.equals(e.owner())).toList();
    }

    @Override public Optional<Faction> faction(FactionId id) { return Optional.ofNullable(factions.get(id)); }
    @Override public Collection<Faction> factions() { return List.copyOf(factions.values()); }
    @Override public Optional<FactionId> factionOf(PlayerId player) { return Optional.ofNullable(playerFaction.get(player)); }

    @Override
    public long resource(FactionId faction, String resource) {
        Faction f = factions.get(faction);
        return f == null ? 0 : f.resource(resource);
    }

    @Override public Optional<PendingItem> pending(String id) { return Optional.ofNullable(pending.get(id)); }

    @Override
    public List<PendingItem> openPending() {
        return pending.values().stream().filter(p -> !p.resolved()).toList();
    }

    @Override public Set<FactionId> lockedIn() { return Collections.unmodifiableSortedSet(lockedIn); }

    @Override
    public List<Command> stagedOrders(FactionId faction) {
        return List.copyOf(orders.getOrDefault(faction, List.of()));
    }

    // ---- mutators (event application only) ----

    public void init(String rulesetId, long seed, Map<String, PhaseMode> phaseModes) {
        this.rulesetId = rulesetId;
        this.seed = seed;
        this.phaseModes = Map.copyOf(phaseModes);
    }

    public void setVersion(long version) { this.version = version; }

    public void enterPhase(int turn, String phase, Instant deadline) {
        this.turn = turn;
        this.phase = phase;
        this.deadline = deadline;
    }

    public void putFaction(Faction faction) {
        factions.put(faction.id(), faction);
        playerFaction.put(faction.player(), faction.id());
    }

    public void putLocation(Location location, Set<LocationId> adjacentTo) {
        for (LocationId n : adjacentTo) {
            if (!locations.containsKey(n)) throw new IllegalArgumentException("Unknown adjacent location " + n);
        }
        locations.put(location.id(), location);
        TreeSet<LocationId> mine = adjacency.computeIfAbsent(location.id(), k -> new TreeSet<>());
        for (LocationId n : adjacentTo) {
            mine.add(n);
            adjacency.computeIfAbsent(n, k -> new TreeSet<>()).add(location.id());
        }
    }

    public void setLocationOwner(LocationId id, FactionId owner) {
        locations.put(id, require(locations.get(id), "location " + id).withOwner(owner));
    }

    public void putEntity(Entity entity) {
        entities.put(entity.id(), entity);
        entitySerial++;
    }

    public void moveEntity(EntityId id, LocationId to) {
        entities.put(id, require(entities.get(id), "entity " + id).movedTo(to));
    }

    public void removeEntity(EntityId id) {
        entities.remove(id);
    }

    public void addResource(FactionId faction, String resource, long delta) {
        factions.put(faction, require(factions.get(faction), "faction " + faction).adjusted(resource, delta));
    }

    public void stage(FactionId faction, Command order) {
        orders.computeIfAbsent(faction, k -> new ArrayList<>()).add(order);
    }

    public void lock(FactionId faction) { lockedIn.add(faction); }

    public void clearOrders() {
        orders.clear();
        lockedIn.clear();
    }

    public void putPending(PendingItem item) { pending.put(item.id(), item); }

    public void updatePending(String id, UnaryOperator<PendingItem> change) {
        pending.put(id, change.apply(require(pending.get(id), "pending item " + id)));
    }

    public void end(FactionId winner) {
        this.ended = true;
        this.winner = winner;
    }

    // ---- determinism check ----

    /** Canonical digest of the whole state. Equal fingerprints after live play and after replay prove determinism. */
    public String fingerprint() {
        StringBuilder sb = new StringBuilder();
        sb.append(version).append('|').append(turn).append('|').append(phase).append('|').append(deadline).append('|')
                .append(ended).append('|').append(winner).append('|').append(entitySerial).append('\n');
        locations.values().forEach(v -> sb.append(v).append('\n'));
        adjacency.forEach((k, v) -> sb.append(k).append("->").append(v).append('\n'));
        entities.values().forEach(v -> sb.append(v).append('\n'));
        factions.values().forEach(v -> sb.append(v).append('\n'));
        pending.values().forEach(v -> sb.append(v).append('\n'));
        sb.append(lockedIn).append('\n');
        orders.forEach((k, v) -> sb.append(k).append("=>").append(v).append('\n'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> T require(T value, String what) {
        if (value == null) throw new IllegalStateException("Unknown " + what);
        return value;
    }
}
