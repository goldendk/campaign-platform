package dev.campaign.kernel.event;

import dev.campaign.kernel.event.CoreEvent.*;
import dev.campaign.kernel.model.Entity;
import dev.campaign.kernel.model.Faction;
import dev.campaign.kernel.model.Location;
import dev.campaign.kernel.model.PendingItem;
import dev.campaign.kernel.state.GameState;

import java.util.Map;

/** The single place where core events change state. The switch is exhaustive: a new CoreEvent will not compile until handled. */
public final class CoreReducer {
    private CoreReducer() {}

    public static void apply(GameState s, CoreEvent event) {
        switch (event) {
            case CampaignCreated e -> s.init(e.rulesetId(), e.seed(), e.phaseModes());
            case FactionAdded e -> s.putFaction(new Faction(e.faction(), e.player(), e.name(), Map.of()));
            case LocationAdded e -> s.putLocation(new Location(e.id(), e.kind(), e.tags(), null), e.adjacentTo());
            case LocationOwnerChanged e -> s.setLocationOwner(e.location(), e.owner());
            case EntityCreated e -> s.putEntity(new Entity(e.id(), e.kind(), e.owner(), e.at(), e.tags(), e.stats()));
            case EntityMoved e -> s.moveEntity(e.entity(), e.to());
            case EntityDestroyed e -> s.removeEntity(e.entity());
            case ResourceChanged e -> s.addResource(e.faction(), e.resource(), e.delta());
            case OrderStaged e -> s.stage(e.faction(), e.order());
            case OrderStagedHidden e -> { }
            case OrderRejected e -> { }
            case FactionLockedIn e -> s.lock(e.faction());
            case OrdersResolved e -> s.clearOrders();
            case PhaseEntered e -> s.enterPhase(e.turn(), e.phase(), e.deadline());
            case PendingOpened e -> s.putPending(PendingItem.open(e.id(), e.kind(), e.parties(), e.options(), e.data()));
            case OutcomeReported e -> s.updatePending(e.pending(), p -> p.withReport(e.faction(), e.outcome()));
            case OutcomeAdjudicated e -> s.updatePending(e.pending(), p -> p.withAdjudication(e.outcome()));
            case PendingDisputed e -> s.updatePending(e.pending(), PendingItem::asDisputed);
            case PendingResolved e -> s.updatePending(e.pending(), p -> p.asResolved(e.outcome()));
            case CampaignEnded e -> s.end(e.winner());
        }
    }
}
