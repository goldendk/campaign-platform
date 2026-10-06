package dev.campaign.kernel.event;

import dev.campaign.kernel.api.Command;
import dev.campaign.kernel.api.Event;
import dev.campaign.kernel.api.PhaseMode;
import dev.campaign.kernel.id.EntityId;
import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.id.LocationId;
import dev.campaign.kernel.id.PlayerId;
import dev.campaign.kernel.model.Participant;
import dev.campaign.kernel.model.Reason;
import dev.campaign.kernel.util.Sorted;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The generic vocabulary every ruleset speaks. Most rulesets never need to define their own events: moving a
 * unit is EntityMoved whether it is an army or a cargo ship; the theme decides how it reads.
 * When you add a record here, add it to ALL and to CoreReducer (the compiler enforces the latter).
 */
public sealed interface CoreEvent extends Event {

    // --- setup ---
    record CampaignCreated(String rulesetId, long seed, List<Participant> participants, Map<String, PhaseMode> phaseModes) implements CoreEvent {
        public CampaignCreated {
            participants = List.copyOf(participants);
            phaseModes = Sorted.map(phaseModes);
        }
    }

    record FactionAdded(FactionId faction, PlayerId player, String name) implements CoreEvent {}

    // --- world ---
    record LocationAdded(LocationId id, String kind, Set<String> tags, Set<LocationId> adjacentTo) implements CoreEvent {
        public LocationAdded {
            tags = Sorted.set(tags);
            adjacentTo = Sorted.set(adjacentTo);
        }
    }

    record LocationOwnerChanged(LocationId location, FactionId owner) implements CoreEvent {}

    record EntityCreated(EntityId id, String kind, FactionId owner, LocationId at, Set<String> tags, Map<String, Long> stats) implements CoreEvent {
        public EntityCreated {
            tags = Sorted.set(tags);
            stats = Sorted.map(stats);
        }
    }

    record EntityMoved(EntityId entity, LocationId from, LocationId to) implements CoreEvent {}

    record EntityDestroyed(EntityId entity, LocationId at, String reason) implements CoreEvent {}

    record ResourceChanged(FactionId faction, String resource, long delta) implements CoreEvent {}

    // --- orders ---
    /** Visible to the issuing faction (and the GM). Everyone else gets OrderStagedHidden. */
    record OrderStaged(FactionId faction, Command order) implements CoreEvent {}

    record OrderStagedHidden(FactionId faction) implements CoreEvent {}

    record OrderRejected(FactionId faction, Command order, List<Reason> reasons) implements CoreEvent {
        public OrderRejected {
            reasons = List.copyOf(reasons);
        }
    }

    record FactionLockedIn(FactionId faction, boolean automatic) implements CoreEvent {}

    record OrdersResolved(String phase) implements CoreEvent {}

    // --- turn sequence ---
    record PhaseEntered(int turn, String phase, Instant deadline) implements CoreEvent {}

    // --- externally resolved items (tabletop battles etc.) ---
    record PendingOpened(String id, String kind, Set<FactionId> parties, List<String> options, Map<String, String> data) implements CoreEvent {
        public PendingOpened {
            parties = Sorted.set(parties);
            options = List.copyOf(options);
            data = Sorted.map(data);
        }
    }

    record OutcomeReported(String pending, FactionId faction, String outcome) implements CoreEvent {}

    record OutcomeAdjudicated(String pending, String outcome) implements CoreEvent {}

    record PendingDisputed(String pending) implements CoreEvent {}

    record PendingResolved(String pending, String outcome, boolean byGm) implements CoreEvent {}

    // --- end ---
    record CampaignEnded(FactionId winner) implements CoreEvent {}

    List<Class<? extends CoreEvent>> ALL = List.of(
            CampaignCreated.class, FactionAdded.class, LocationAdded.class, LocationOwnerChanged.class,
            EntityCreated.class, EntityMoved.class, EntityDestroyed.class, ResourceChanged.class,
            OrderStaged.class, OrderStagedHidden.class, OrderRejected.class, FactionLockedIn.class,
            OrdersResolved.class, PhaseEntered.class, PendingOpened.class, OutcomeReported.class,
            OutcomeAdjudicated.class, PendingDisputed.class, PendingResolved.class, CampaignEnded.class);
}
