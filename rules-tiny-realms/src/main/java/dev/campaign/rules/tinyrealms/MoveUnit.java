package dev.campaign.rules.tinyrealms;

import dev.campaign.kernel.api.Command;
import dev.campaign.kernel.api.CommandHandler;
import dev.campaign.kernel.api.Ctx;
import dev.campaign.kernel.api.Emission;
import dev.campaign.kernel.api.RequiredTag;
import dev.campaign.kernel.event.CoreEvent.EntityMoved;
import dev.campaign.kernel.id.EntityId;
import dev.campaign.kernel.id.LocationId;
import dev.campaign.kernel.model.Entity;
import dev.campaign.kernel.model.Validation;
import dev.campaign.kernel.state.StateReader;

import java.util.List;
import java.util.Optional;

/**
 * Move one unit along a path of adjacent locations.
 *
 * This file is the complete implementation of the command: the record declares the parameters (and thereby the UI
 * prompt: "pick a mobile unit, then pick a path"), the handler holds the rules. Nothing else exists for it:
 * no controller, DTO, mapper or table. Whether this is "march an army" or "sail a freighter" is a theme matter.
 */
public record MoveUnit(@RequiredTag("mobile") EntityId unit, List<LocationId> path) implements Command {

    public MoveUnit {
        path = List.copyOf(path);
    }

    public static final class Handler implements CommandHandler<MoveUnit> {
        @Override
        public Validation validate(Ctx ctx, MoveUnit c) {
            StateReader s = ctx.state();
            Optional<Entity> found = s.entity(c.unit());
            if (found.isEmpty()) return Validation.fail("move.not_found", "unit", c.unit().value());
            Entity unit = found.get();
            if (!ctx.faction().map(f -> f.equals(unit.owner())).orElse(false)) return Validation.fail("move.not_yours", "unit", unit.id().value());
            if (!unit.has("mobile")) return Validation.fail("move.not_mobile", "unit", unit.id().value());
            if (c.path().isEmpty()) return Validation.fail("move.empty_path");
            long allowance = unit.stat("move", 1);
            if (c.path().size() > allowance) return Validation.fail("move.too_far", "max", String.valueOf(allowance));
            LocationId at = unit.at();
            for (LocationId step : c.path()) {
                if (!s.neighbours(at).contains(step)) {
                    return Validation.fail("move.not_adjacent", "from", at.value(), "to", step.value());
                }
                at = step;
            }
            return Validation.ok();
        }

        @Override
        public List<Emission> decide(Ctx ctx, MoveUnit c) {
            Entity unit = ctx.state().entity(c.unit()).orElseThrow();
            LocationId destination = c.path().get(c.path().size() - 1);
            return List.of(Emission.publicly(new EntityMoved(unit.id(), unit.at(), destination)));
        }
    }
}
