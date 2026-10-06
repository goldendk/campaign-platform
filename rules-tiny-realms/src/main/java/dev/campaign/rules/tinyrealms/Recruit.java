package dev.campaign.rules.tinyrealms;

import dev.campaign.kernel.api.Command;
import dev.campaign.kernel.api.CommandHandler;
import dev.campaign.kernel.api.Ctx;
import dev.campaign.kernel.api.Emission;
import dev.campaign.kernel.event.CoreEvent.EntityCreated;
import dev.campaign.kernel.event.CoreEvent.ResourceChanged;
import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.id.LocationId;
import dev.campaign.kernel.model.Location;
import dev.campaign.kernel.model.Validation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Raise a new unit at one of your keeps, for gold. */
public record Recruit(LocationId at) implements Command {

    public static final class Handler implements CommandHandler<Recruit> {
        @Override
        public Validation validate(Ctx ctx, Recruit c) {
            FactionId faction = ctx.faction().orElseThrow();
            Optional<Location> location = ctx.state().location(c.at());
            if (location.isEmpty()) return Validation.fail("recruit.no_such_location", "location", c.at().value());
            Location loc = location.get();
            if (!loc.tags().contains("keep") || !faction.equals(loc.owner())) {
                return Validation.fail("recruit.not_keep", "location", loc.id().value());
            }
            if (ctx.state().resource(faction, TinyRealms.GOLD) < TinyRealms.RECRUIT_COST) {
                return Validation.fail("recruit.too_poor", "cost", String.valueOf(TinyRealms.RECRUIT_COST));
            }
            return Validation.ok();
        }

        @Override
        public List<Emission> decide(Ctx ctx, Recruit c) {
            FactionId faction = ctx.faction().orElseThrow();
            return List.of(
                    Emission.publicly(new ResourceChanged(faction, TinyRealms.GOLD, -TinyRealms.RECRUIT_COST)),
                    Emission.publicly(new EntityCreated(ctx.newEntityId("warband"), "warband", faction, c.at(),
                            Set.of("unit", "mobile"), Map.of("move", 2L, "strength", 3L))));
        }
    }
}
