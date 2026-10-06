package dev.campaign.rules.tinyrealms;

import dev.campaign.kernel.api.CampaignConfig;
import dev.campaign.kernel.api.CommandSpec;
import dev.campaign.kernel.api.Ctx;
import dev.campaign.kernel.api.Emission;
import dev.campaign.kernel.api.PhaseDef;
import dev.campaign.kernel.api.Ruleset;
import dev.campaign.kernel.api.RulesetRegistry;
import dev.campaign.kernel.event.CoreEvent.EntityCreated;
import dev.campaign.kernel.event.CoreEvent.EntityDestroyed;
import dev.campaign.kernel.event.CoreEvent.LocationAdded;
import dev.campaign.kernel.event.CoreEvent.LocationOwnerChanged;
import dev.campaign.kernel.event.CoreEvent.PendingOpened;
import dev.campaign.kernel.event.CoreEvent.ResourceChanged;
import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.id.LocationId;
import dev.campaign.kernel.model.Entity;
import dev.campaign.kernel.model.Faction;
import dev.campaign.kernel.model.Location;
import dev.campaign.kernel.model.PendingItem;
import dev.campaign.kernel.model.Participant;
import dev.campaign.kernel.state.StateReader;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A deliberately tiny campaign ruleset used to prove the framework and to document it by example.
 *
 * Turn: orders (simultaneous, 24h) -> battles (wait for tabletop results) -> upkeep (income).
 * Map: six locations, two keeps. Units: warbands. Win: control four locations.
 * Expects exactly two participants; the first owns "red-keep", the second "blue-keep".
 */
public final class TinyRealms implements Ruleset {
    public static final String ID = "tiny-realms";
    public static final String GOLD = "gold";
    public static final long RECRUIT_COST = 2;
    public static final int LOCATIONS_TO_WIN = 4;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void register(RulesetRegistry registry) {
        registry.phase(PhaseDef.orders("orders", Duration.ofHours(24)));
        registry.phase(PhaseDef.pending("battles"));
        registry.phase(PhaseDef.automatic("upkeep"));

        // Staged commands resolve in priority order: grow the map, then recruit, then move.
        registry.command(CommandSpec.staged(PlaceTile.class, new PlaceTile.Handler(), 1, "orders"));
        registry.command(CommandSpec.staged(Recruit.class, new Recruit.Handler(), 5, "orders"));
        registry.command(CommandSpec.staged(MoveUnit.class, new MoveUnit.Handler(), 10, "orders"));

        registry.event(IncomeCollected.class, (state, e) -> state.addResource(e.faction(), GOLD, e.gold()));

        registry.afterOrders("orders", TinyRealms::claimAndContest);
        registry.onPendingResolved("battle", TinyRealms::resolveBattle);
        registry.onEnter("upkeep", TinyRealms::collectIncome);
        registry.victory(TinyRealms::controlsEnough);
    }

    @Override
    public List<Emission> setup(CampaignConfig config) {
        List<Participant> ps = config.participants();
        if (ps.size() != 2) throw new IllegalArgumentException("tiny-realms needs exactly two participants");
        FactionId first = ps.get(0).faction();
        FactionId second = ps.get(1).faction();

        List<Emission> out = new ArrayList<>();
        // red-keep - wood - ford - hill - blue-keep, with a mine between wood and hill.
        out.add(location("red-keep", "castle", Set.of("keep"), Set.of()));
        out.add(location("wood", "forest", Set.of(), Set.of("red-keep")));
        out.add(location("ford", "river", Set.of(), Set.of("wood")));
        out.add(location("mine", "mountain", Set.of(), Set.of("wood")));
        out.add(location("hill", "hill", Set.of(), Set.of("ford", "mine")));
        out.add(location("blue-keep", "castle", Set.of("keep"), Set.of("hill")));

        out.add(Emission.publicly(new LocationOwnerChanged(LocationId.of("red-keep"), first)));
        out.add(Emission.publicly(new LocationOwnerChanged(LocationId.of("blue-keep"), second)));

        out.add(warband("red-w1", first, "red-keep"));
        out.add(warband("blue-w1", second, "blue-keep"));

        out.add(Emission.publicly(new ResourceChanged(first, GOLD, 3)));
        out.add(Emission.publicly(new ResourceChanged(second, GOLD, 3)));
        return out;
    }

    // ------------------------------------------------------------------ hooks (pure functions of state)

    /** After all orders: unopposed arrivals claim ground; locations with several factions become tabletop battles. */
    private static List<Emission> claimAndContest(Ctx ctx) {
        StateReader s = ctx.state();
        List<Emission> out = new ArrayList<>();
        for (Location loc : s.locations()) {
            Set<FactionId> present = new TreeSet<>();
            for (Entity e : s.entitiesAt(loc.id())) if (e.owner() != null) present.add(e.owner());

            if (present.size() == 1) {
                FactionId only = present.iterator().next();
                if (!only.equals(loc.owner())) out.add(Emission.publicly(new LocationOwnerChanged(loc.id(), only)));
            } else if (present.size() > 1) {
                List<String> options = new ArrayList<>();
                for (FactionId f : present) options.add(f.value());
                options.add("draw");
                out.add(Emission.publicly(new PendingOpened(
                        "battle-t" + s.turn() + "-" + loc.id(), "battle", present, options, Map.of("location", loc.id().value()))));
            }
        }
        return out;
    }

    /** The winner holds the field: the others' units there are destroyed and the location changes hands. */
    private static List<Emission> resolveBattle(Ctx ctx, PendingItem item, String outcome) {
        List<Emission> out = new ArrayList<>();
        if (outcome.equals("draw")) return out;
        LocationId at = LocationId.of(item.data().get("location"));
        FactionId winner = FactionId.of(outcome);
        for (Entity e : ctx.state().entitiesAt(at)) {
            if (!winner.equals(e.owner())) out.add(Emission.publicly(new EntityDestroyed(e.id(), at, "battle")));
        }
        Optional<Location> location = ctx.state().location(at);
        if (location.isPresent() && !winner.equals(location.get().owner())) {
            out.add(Emission.publicly(new LocationOwnerChanged(at, winner)));
        }
        return out;
    }

    /** One gold per owned location. */
    private static List<Emission> collectIncome(Ctx ctx) {
        List<Emission> out = new ArrayList<>();
        for (Faction f : ctx.state().factions()) {
            long owned = ctx.state().locations().stream().filter(l -> f.id().equals(l.owner())).count();
            if (owned > 0) out.add(Emission.publicly(new IncomeCollected(f.id(), owned, owned)));
        }
        return out;
    }

    private static Optional<FactionId> controlsEnough(StateReader s) {
        Map<FactionId, Integer> owned = new TreeMap<>();
        for (Location l : s.locations()) if (l.owner() != null) owned.merge(l.owner(), 1, Integer::sum);
        return owned.entrySet().stream().filter(e -> e.getValue() >= LOCATIONS_TO_WIN).map(Map.Entry::getKey).findFirst();
    }

    // ------------------------------------------------------------------ setup helpers

    private static Emission location(String id, String kind, Set<String> tags, Set<String> adjacentTo) {
        Set<LocationId> neighbours = new TreeSet<>();
        for (String n : adjacentTo) neighbours.add(LocationId.of(n));
        return Emission.publicly(new LocationAdded(LocationId.of(id), kind, tags, neighbours));
    }

    private static Emission warband(String id, FactionId owner, String at) {
        return Emission.publicly(new EntityCreated(dev.campaign.kernel.id.EntityId.of(id), "warband", owner,
                LocationId.of(at), Set.of("unit", "mobile"), Map.of("move", 2L, "strength", 3L)));
    }
}
