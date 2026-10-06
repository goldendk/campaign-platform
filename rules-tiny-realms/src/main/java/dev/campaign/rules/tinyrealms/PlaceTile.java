package dev.campaign.rules.tinyrealms;

import dev.campaign.kernel.api.Command;
import dev.campaign.kernel.api.CommandHandler;
import dev.campaign.kernel.api.Ctx;
import dev.campaign.kernel.api.Emission;
import dev.campaign.kernel.event.CoreEvent.LocationAdded;
import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.id.LocationId;
import dev.campaign.kernel.model.Location;
import dev.campaign.kernel.model.Validation;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Grow the map: add a new location next to one you own. The world graph grows during play. */
public record PlaceTile(LocationId from, LocationId id, String terrain) implements Command {

    static final Set<String> TERRAINS = Set.of("forest", "hill", "marsh", "river", "mountain");

    public static final class Handler implements CommandHandler<PlaceTile> {
        @Override
        public Validation validate(Ctx ctx, PlaceTile c) {
            FactionId faction = ctx.faction().orElseThrow();
            Optional<Location> from = ctx.state().location(c.from());
            if (from.isEmpty() || !faction.equals(from.get().owner())) return Validation.fail("tile.not_owned", "location", c.from().value());
            if (ctx.state().location(c.id()).isPresent()) return Validation.fail("tile.exists", "location", c.id().value());
            if (!TERRAINS.contains(c.terrain())) return Validation.fail("tile.bad_terrain", "terrain", c.terrain());
            return Validation.ok();
        }

        @Override
        public List<Emission> decide(Ctx ctx, PlaceTile c) {
            return List.of(Emission.publicly(new LocationAdded(c.id(), c.terrain(), Set.of(), Set.of(c.from()))));
        }
    }
}
