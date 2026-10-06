package dev.campaign.kernel.engine;

import dev.campaign.kernel.api.CampaignConfig;
import dev.campaign.kernel.api.Command;
import dev.campaign.kernel.api.CommandSpec;
import dev.campaign.kernel.api.Ctx;
import dev.campaign.kernel.api.Emission;
import dev.campaign.kernel.api.Event;
import dev.campaign.kernel.api.Hook;
import dev.campaign.kernel.api.PendingHook;
import dev.campaign.kernel.api.PhaseDef;
import dev.campaign.kernel.api.PhaseKind;
import dev.campaign.kernel.api.Ruleset;
import dev.campaign.kernel.api.RulesetRegistry;
import dev.campaign.kernel.api.VictoryCondition;
import dev.campaign.kernel.event.CoreEvent;
import dev.campaign.kernel.event.CoreEvent.CampaignCreated;
import dev.campaign.kernel.event.CoreEvent.CampaignEnded;
import dev.campaign.kernel.event.CoreEvent.FactionAdded;
import dev.campaign.kernel.event.CoreEvent.FactionLockedIn;
import dev.campaign.kernel.event.CoreEvent.OrderRejected;
import dev.campaign.kernel.event.CoreEvent.OrderStaged;
import dev.campaign.kernel.event.CoreEvent.OrderStagedHidden;
import dev.campaign.kernel.event.CoreEvent.OrdersResolved;
import dev.campaign.kernel.event.CoreEvent.PendingDisputed;
import dev.campaign.kernel.event.CoreEvent.PendingResolved;
import dev.campaign.kernel.event.CoreEvent.PhaseEntered;
import dev.campaign.kernel.event.CoreReducer;
import dev.campaign.kernel.event.StoredEvent;
import dev.campaign.kernel.event.ViewEvent;
import dev.campaign.kernel.id.CampaignId;
import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.model.Actor;
import dev.campaign.kernel.model.Faction;
import dev.campaign.kernel.model.PendingItem;
import dev.campaign.kernel.model.Participant;
import dev.campaign.kernel.model.Role;
import dev.campaign.kernel.model.Validation;
import dev.campaign.kernel.state.GameState;
import dev.campaign.kernel.state.StateReader;
import dev.campaign.kernel.store.EventStore;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * The campaign aggregate: one instance per running campaign, driven by commands, producing events.
 *
 * <pre>
 *   command ──validate──► stage (private)  ─┐
 *                                            ├─► phase complete ─► resolve orders ─► hooks ─► next phase
 *   command ──validate──► decide (immediate)─┘
 * </pre>
 *
 * Not thread-safe: the host must serialise calls per campaign (game-app does). Every public mutating call is one
 * transaction: all resulting events are appended to the store together or not at all. If a call fails midway the
 * instance marks itself broken and must be reloaded with {@link #load}.
 */
public final class Campaign {
    private final CampaignId id;
    private final RulesetRegistry registry;
    private final EventStore store;
    private final Clock clock;
    private final GameState state = new GameState();
    private long lastSeq;
    private List<StoredEvent> buffer;
    private boolean broken;

    private Campaign(CampaignId id, Ruleset ruleset, EventStore store, Clock clock) {
        this.id = id;
        this.registry = RulesetRegistry.build(ruleset);
        this.store = store;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ lifecycle

    public static Campaign create(CampaignId id, Ruleset ruleset, CampaignConfig config, EventStore store, Clock clock) {
        if (!store.load(id).isEmpty()) throw new IllegalStateException("Campaign already exists: " + id);
        Campaign c = new Campaign(id, ruleset, store, clock);
        c.transact(() -> {
            c.emit(Emission.publicly(new CampaignCreated(ruleset.id(), config.seed(), config.participants(), config.phaseModes())));
            for (Participant p : config.participants()) {
                c.emit(Emission.publicly(new FactionAdded(p.faction(), p.player(), p.name())));
            }
            c.emitAll(ruleset.setup(config));
            c.enterPhase(1, c.registry.phases().get(0));
            c.settle();
            return null;
        });
        return c;
    }

    /** Rebuild by replaying the stored events. No rules run during replay; only reducers do. */
    public static Campaign load(CampaignId id, Ruleset ruleset, EventStore store, Clock clock) {
        List<StoredEvent> events = store.load(id);
        if (events.isEmpty()) throw new NoSuchElementException("Unknown campaign: " + id);
        return replay(id, ruleset, store, clock, events);
    }

    /** Same, picking the ruleset recorded in the campaign's first event. */
    public static Campaign load(CampaignId id, Map<String, Ruleset> catalog, EventStore store, Clock clock) {
        List<StoredEvent> events = store.load(id);
        if (events.isEmpty()) throw new NoSuchElementException("Unknown campaign: " + id);
        if (!(events.get(0).event() instanceof CampaignCreated created)) {
            throw new IllegalStateException("Campaign log does not start with CampaignCreated: " + id);
        }
        Ruleset ruleset = catalog.get(created.rulesetId());
        if (ruleset == null) throw new IllegalStateException("Ruleset not installed: " + created.rulesetId());
        return replay(id, ruleset, store, clock, events);
    }

    private static Campaign replay(CampaignId id, Ruleset ruleset, EventStore store, Clock clock, List<StoredEvent> events) {
        Campaign c = new Campaign(id, ruleset, store, clock);
        for (StoredEvent e : events) {
            c.apply(e);
            c.lastSeq = e.seq();
        }
        if (!ruleset.id().equals(c.state.rulesetId())) {
            throw new IllegalStateException("Campaign " + id + " was created with ruleset " + c.state.rulesetId());
        }
        return c;
    }

    // ------------------------------------------------------------------ public API

    public CampaignId id() {
        return id;
    }

    public StateReader state() {
        return state;
    }

    public String fingerprint() {
        return state.fingerprint();
    }

    public RulesetRegistry registry() {
        return registry;
    }

    public SubmitResult submit(Actor actor, Command command) {
        return submit(actor, state.turn(), state.phase(), command);
    }

    /**
     * turn and phase are what the client believed when it composed the command. If the campaign has moved on
     * the command is rejected as stale (rather than silently applying to a different situation).
     */
    public SubmitResult submit(Actor actor, int turn, String phase, Command command) {
        return transact(() -> doSubmit(actor, turn, phase, command));
    }

    /** Called by a scheduler. Locks in everyone who missed the deadline of an ORDERS phase and resolves it. */
    public SubmitResult tick() {
        return transact(() -> {
            long before = lastSeq;
            if (!state.ended() && state.deadline() != null && !clock.instant().isBefore(state.deadline())) {
                PhaseDef def = currentPhase();
                if (def.kind() == PhaseKind.ORDERS) {
                    for (Faction f : state.factions()) {
                        if (!state.lockedIn().contains(f.id())) emit(Emission.publicly(new FactionLockedIn(f.id(), true)));
                    }
                    settle();
                }
            }
            return SubmitResult.accepted(lastSeq, lastSeq - before);
        });
    }

    public PlayerView viewFor(Actor viewer) {
        return ViewBuilder.build(state, viewer, registry.viewPolicy());
    }

    /** Events after afterSeq, each in the form this viewer may see (full, redacted, or omitted). */
    public List<ViewEvent> eventsFor(Actor viewer, long afterSeq) {
        FactionId viewerFaction = viewer.isGm() ? null : state.factionOf(viewer.player()).orElse(null);
        List<ViewEvent> out = new ArrayList<>();
        for (StoredEvent stored : store.load(id, afterSeq)) {
            Event visible = null;
            if (stored.audience().canSee(viewer, viewerFaction)) visible = stored.event();
            else if (stored.redacted() != null) visible = stored.redacted();
            if (visible != null) out.add(new ViewEvent(stored.seq(), stored.turn(), stored.phase(), visible));
        }
        return out;
    }

    public List<CommandSchema.Descriptor> commandSchemas() {
        return registry.commands().stream().map(CommandSchema::describe)
                .sorted(Comparator.comparing(CommandSchema.Descriptor::command)).toList();
    }

    // ------------------------------------------------------------------ submit

    private SubmitResult doSubmit(Actor actor, int turn, String phase, Command command) {
        if (state.ended()) return SubmitResult.rejected("campaign.ended");
        if (turn != state.turn() || !state.phase().equals(phase)) {
            return SubmitResult.rejected("command.stale_phase", "turn", String.valueOf(state.turn()), "phase", state.phase());
        }
        CommandSpec<?> spec = registry.commandSpec(command.getClass());
        if (spec == null) return SubmitResult.rejected("command.unknown", "command", command.getClass().getSimpleName());

        Optional<FactionId> faction = actor.isGm() ? Optional.empty() : state.factionOf(actor.player());
        if (spec.who() == CommandSpec.Who.GM && !actor.isGm()) return SubmitResult.rejected("command.forbidden");
        if (spec.who() == CommandSpec.Who.PLAYER && faction.isEmpty()) return SubmitResult.rejected("command.forbidden");
        if (!spec.allowedIn(state.phase())) return SubmitResult.rejected("command.not_in_phase", "phase", state.phase());

        PhaseDef def = currentPhase();
        if (spec.mode() == CommandSpec.Mode.STAGED) {
            if (def.kind() != PhaseKind.ORDERS) return SubmitResult.rejected("orders.not_open");
            PhaseStrategy strategy = PhaseStrategy.forMode(state.phaseMode(def.id(), def.mode()));
            if (!strategy.mayStage(state, faction.orElseThrow())) return SubmitResult.rejected("orders.already_locked");
        }

        Ctx ctx = ctx(actor);
        Validation validation = spec.validate(ctx, command);
        if (!validation.isValid()) return SubmitResult.rejected(validation.reasons());

        long before = lastSeq;
        if (spec.mode() == CommandSpec.Mode.STAGED) {
            FactionId f = faction.orElseThrow();
            emit(Emission.toFaction(f, new OrderStaged(f, command), new OrderStagedHidden(f)));
        } else {
            emitAll(spec.decide(ctx, command));
        }
        settle();
        return SubmitResult.accepted(lastSeq, lastSeq - before);
    }

    // ------------------------------------------------------------------ the turn machine

    /** Moves the campaign forward for as long as the current phase is complete. */
    private void settle() {
        int guard = 0;
        while (!state.ended()) {
            if (++guard > 1000) throw new IllegalStateException("Turn machine did not settle; check hooks for loops");
            if (settlePending()) continue;
            PhaseDef def = currentPhase();
            if (!isComplete(def)) break;
            leavePhase(def);
        }
    }

    /** Turns reports/rulings into resolutions. Returns true if anything happened. */
    private boolean settlePending() {
        boolean progressed = false;
        for (PendingItem p : state.openPending()) {
            if (p.adjudication() != null) {
                resolvePending(p, p.adjudication(), true);
                progressed = true;
            } else if (p.reports().size() == p.parties().size()) {
                Set<String> distinct = new TreeSet<>(p.reports().values());
                if (distinct.size() == 1) {
                    resolvePending(p, distinct.iterator().next(), false);
                    progressed = true;
                } else if (!p.disputed()) {
                    emit(Emission.publicly(new PendingDisputed(p.id())));
                    progressed = true;
                }
            }
        }
        if (progressed) checkVictory();
        return progressed;
    }

    private void resolvePending(PendingItem item, String outcome, boolean byGm) {
        emit(Emission.publicly(new PendingResolved(item.id(), outcome, byGm)));
        PendingHook hook = registry.pendingHook(item.kind());
        if (hook != null) {
            PendingItem resolved = state.pending(item.id()).orElseThrow();
            emitAll(hook.run(ctx(null), resolved, outcome));
        }
    }

    private boolean isComplete(PhaseDef def) {
        return switch (def.kind()) {
            case AUTOMATIC -> true;
            case PENDING -> state.openPending().isEmpty();
            case ORDERS -> PhaseStrategy.forMode(state.phaseMode(def.id(), def.mode())).isComplete(state);
        };
    }

    private void leavePhase(PhaseDef def) {
        if (def.kind() == PhaseKind.ORDERS) resolveOrders(def);
        checkVictory();
        if (state.ended()) return;

        List<PhaseDef> phases = registry.phases();
        int index = phases.indexOf(def);
        if (index + 1 < phases.size()) {
            enterPhase(state.turn(), phases.get(index + 1));
        } else {
            enterPhase(state.turn() + 1, phases.get(0));
        }
    }

    private void enterPhase(int turn, PhaseDef def) {
        Instant deadline = def.deadline() == null ? null : clock.instant().plus(def.deadline());
        emit(Emission.publicly(new PhaseEntered(turn, def.id(), deadline)));
        for (Hook hook : registry.enterHooks(def.id())) emitAll(hook.run(ctx(null)));
        checkVictory();
    }

    private void resolveOrders(PhaseDef def) {
        record Staged(FactionId faction, int index, Command command, CommandSpec<?> spec) {}

        List<Staged> all = new ArrayList<>();
        for (Faction f : state.factions()) {
            List<Command> staged = state.stagedOrders(f.id());
            for (int i = 0; i < staged.size(); i++) {
                all.add(new Staged(f.id(), i, staged.get(i), registry.commandSpec(staged.get(i).getClass())));
            }
        }
        // Deterministic order: spec priority, then faction id, then the order in which the player issued them.
        all.sort(Comparator.<Staged>comparingInt(s -> s.spec().priority())
                .thenComparing(Staged::faction)
                .thenComparingInt(Staged::index));

        for (Staged s : all) {
            Faction f = state.faction(s.faction()).orElseThrow();
            Ctx ctx = ctx(new Actor(f.player(), Role.PLAYER));
            Validation validation = s.spec().validate(ctx, s.command());
            if (!validation.isValid()) {
                emit(Emission.toFaction(s.faction(), new OrderRejected(s.faction(), s.command(), validation.reasons()), null));
                continue;
            }
            emitAll(s.spec().decide(ctx, s.command()));
        }
        emit(Emission.publicly(new OrdersResolved(def.id())));
        for (Hook hook : registry.afterOrdersHooks(def.id())) emitAll(hook.run(ctx(null)));
    }

    private void checkVictory() {
        if (state.ended()) return;
        for (VictoryCondition condition : registry.victoryConditions()) {
            Optional<FactionId> winner = condition.check(state);
            if (winner.isPresent()) {
                emit(Emission.publicly(new CampaignEnded(winner.get())));
                return;
            }
        }
    }

    // ------------------------------------------------------------------ plumbing

    private PhaseDef currentPhase() {
        PhaseDef def = registry.phase(state.phase());
        if (def == null) throw new IllegalStateException("Unknown phase: " + state.phase());
        return def;
    }

    private Ctx ctx(Actor actor) {
        return new Ctx(state, actor, registry.phase(state.phase()), clock.instant());
    }

    private void emitAll(List<Emission> emissions) {
        for (Emission e : emissions) emit(e);
    }

    private void emit(Emission emission) {
        long seq = lastSeq + 1;
        int turn = state.turn();
        String phase = state.phase();
        if (emission.event() instanceof PhaseEntered entered) {
            turn = entered.turn();
            phase = entered.phase();
        }
        StoredEvent stored = new StoredEvent(seq, clock.instant(), turn, phase,
                emission.audience(), emission.event(), emission.redacted());
        apply(stored);
        lastSeq = seq;
        buffer.add(stored);
    }

    private void apply(StoredEvent stored) {
        Event event = stored.event();
        if (event instanceof CoreEvent core) {
            CoreReducer.apply(state, core);
        } else {
            var reducer = registry.reducer(event.getClass());
            if (reducer == null) throw new IllegalStateException("No reducer registered for " + event.getClass().getName());
            reducer.accept(state, event);
        }
        state.setVersion(stored.seq());
    }

    private <T> T transact(Supplier<T> work) {
        if (broken) throw new IllegalStateException("Campaign instance is inconsistent after a failure; reload it from the store");
        List<StoredEvent> events = new ArrayList<>();
        buffer = events;
        long startSeq = lastSeq;
        try {
            T result = work.get();
            if (!events.isEmpty()) store.append(id, startSeq, events);
            return result;
        } catch (RuntimeException | Error failure) {
            broken = true;
            throw failure;
        } finally {
            buffer = null;
        }
    }
}
