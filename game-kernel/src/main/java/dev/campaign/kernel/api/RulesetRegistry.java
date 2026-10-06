package dev.campaign.kernel.api;

import dev.campaign.kernel.command.CoreCommands;
import dev.campaign.kernel.event.CoreEvent;
import dev.campaign.kernel.state.GameState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/**
 * Where a ruleset declares itself. Built once per campaign instance: core registrations first (scope "core"),
 * then the ruleset's own (scope = ruleset id). The scope prefixes JSON type names, so two rulesets can both
 * define a "MoveUnit" without clashing.
 */
public final class RulesetRegistry {
    private String scope = "core";
    private final List<PhaseDef> phases = new ArrayList<>();
    private final Map<Class<?>, CommandSpec<?>> commands = new HashMap<>();
    private final Map<String, Class<? extends Command>> commandTypes = new TreeMap<>();
    private final Map<String, Class<? extends Event>> eventTypes = new TreeMap<>();
    private final Map<Class<?>, BiConsumer<GameState, Event>> reducers = new HashMap<>();
    private final Map<String, List<Hook>> enterHooks = new HashMap<>();
    private final Map<String, List<Hook>> afterOrdersHooks = new HashMap<>();
    private final Map<String, PendingHook> pendingHooks = new HashMap<>();
    private final List<VictoryCondition> victoryConditions = new ArrayList<>();
    private ViewPolicy viewPolicy = ViewPolicy.everythingVisible();

    private RulesetRegistry() {}

    /** Core registrations + the ruleset's own. This is what Campaign uses; game-json uses it to learn type names. */
    public static RulesetRegistry build(Ruleset ruleset) {
        RulesetRegistry registry = new RulesetRegistry();
        registry.scope = "core";
        CoreCommands.register(registry);
        CoreEvent.ALL.forEach(registry::event);
        registry.scope = ruleset.id();
        ruleset.register(registry);
        if (registry.phases.isEmpty()) {
            throw new IllegalStateException("Ruleset " + ruleset.id() + " declares no phases");
        }
        return registry;
    }

    // ---- declaration API (used by rulesets) ----

    public RulesetRegistry phase(PhaseDef phase) {
        phases.add(phase);
        return this;
    }

    public <C extends Command> RulesetRegistry command(CommandSpec<C> spec) {
        commands.put(spec.type(), spec);
        commandTypes.put(scope + ":" + spec.id(), spec.type());
        return this;
    }

    /** Register an event type that changes no state by itself (narration only). */
    public RulesetRegistry event(Class<? extends Event> type) {
        eventTypes.put(scope + ":" + type.getSimpleName(), type);
        return this;
    }

    /** Register a ruleset-specific event together with the reducer that applies it to the state. */
    public <E extends Event> RulesetRegistry event(Class<E> type, BiConsumer<GameState, E> reducer) {
        event(type);
        reducers.put(type, (state, event) -> reducer.accept(state, type.cast(event)));
        return this;
    }

    public RulesetRegistry onEnter(String phaseId, Hook hook) {
        enterHooks.computeIfAbsent(phaseId, k -> new ArrayList<>()).add(hook);
        return this;
    }

    public RulesetRegistry afterOrders(String phaseId, Hook hook) {
        afterOrdersHooks.computeIfAbsent(phaseId, k -> new ArrayList<>()).add(hook);
        return this;
    }

    public RulesetRegistry onPendingResolved(String pendingKind, PendingHook hook) {
        pendingHooks.put(pendingKind, hook);
        return this;
    }

    public RulesetRegistry victory(VictoryCondition condition) {
        victoryConditions.add(condition);
        return this;
    }

    public RulesetRegistry viewPolicy(ViewPolicy policy) {
        this.viewPolicy = policy;
        return this;
    }

    // ---- lookup API (used by the engine and adapters) ----

    public List<PhaseDef> phases() {
        return List.copyOf(phases);
    }

    public PhaseDef phase(String id) {
        for (PhaseDef p : phases) if (p.id().equals(id)) return p;
        return null;
    }

    public CommandSpec<?> commandSpec(Class<?> type) {
        return commands.get(type);
    }

    public Collection<CommandSpec<?>> commands() {
        return List.copyOf(commands.values());
    }

    public Map<String, Class<? extends Command>> commandTypes() {
        return Map.copyOf(commandTypes);
    }

    public Map<String, Class<? extends Event>> eventTypes() {
        return Map.copyOf(eventTypes);
    }

    public BiConsumer<GameState, Event> reducer(Class<?> eventType) {
        return reducers.get(eventType);
    }

    public List<Hook> enterHooks(String phaseId) {
        return List.copyOf(enterHooks.getOrDefault(phaseId, List.of()));
    }

    public List<Hook> afterOrdersHooks(String phaseId) {
        return List.copyOf(afterOrdersHooks.getOrDefault(phaseId, List.of()));
    }

    public PendingHook pendingHook(String kind) {
        return pendingHooks.get(kind);
    }

    public List<VictoryCondition> victoryConditions() {
        return List.copyOf(victoryConditions);
    }

    public ViewPolicy viewPolicy() {
        return viewPolicy;
    }
}
