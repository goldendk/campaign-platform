# Architecture notes

## The shape

```
 HTTP / JDBC / timer          game-app (Spring)  ── adapters only
            │
   CampaignJson (game-json)   records <-> JSON, nothing else
            │
 ┌──────────▼───────────────────────────────────────────────┐
 │ game-kernel                                              │
 │   Campaign: submit() / tick() / viewFor() / eventsFor()  │
 │   phases ─ staged orders ─ pending items ─ hooks         │
 │   events (CoreEvent + ruleset events) ─ reducers         │
 │   ports: EventStore, Clock                               │
 └──────────▲──────────────────────────────▲────────────────┘
            │ Ruleset (ServiceLoader)      │ Theme / Narrator
   rules-tiny-realms                  theme-tiny-realms
```

## Decisions and why

**One shape per concept, no mapping layers.** A command is a record; it is the request body, the staged order and the
handler input. An event is a record; it is what the handler returns, what is stored, what is replayed and (filtered)
what is sent to clients. `StoredEvent` is the persisted row. There is no DTO/entity/persistence split to maintain.

**State changes only through events.** `decide()` returns events; `CoreReducer` (exhaustive switch over a sealed
interface) and ruleset-registered reducers apply them. Replay applies reducers only, never rules, so changing a rule
cannot corrupt old campaigns' state, and old logs stay readable.

**Server-authoritative, events not commands, are the log.** Staged orders are secret and dice must not be
predictable, so clients never replay commands. They receive events filtered through each event's `Audience`; outsiders
get the event's `redacted` form (e.g. "Blue issued orders") or nothing. The GM sees everything.

**Staged vs immediate commands.** Orders in a simultaneous phase are staged privately and executed together when
everyone has locked in (or the deadline passes), in a deterministic order: spec priority, then faction id, then issue
order. Each order is re-validated at execution time; failures become `OrderRejected` for the owner. Immediate commands
(lock in, report a battle, GM ruling) take effect at once.

**Stale-command protection uses (turn, phase), not a global sequence number.** A global version check would reject
Alice's command merely because Bob staged an order a moment earlier, which is wrong for simultaneous play. Instead a
command carries the turn and phase it was composed for and is refused if the campaign has moved on. Durable
concurrency (two servers) is the `EventStore`'s optimistic `expectedLastSeq` check.

**Phases are data; modes are configuration.** A ruleset declares `PhaseDef(id, kind, mode, deadline)`. Kinds: `ORDERS`
(players stage), `PENDING` (wait for external results), `AUTOMATIC` (hooks only). `CampaignConfig.phaseModes` overrides
the mode per phase and is recorded in the first event. Only `SIMULTANEOUS` is implemented; `SEQUENTIAL` is one new
`PhaseStrategy`.

**External resolution is a kernel feature.** Tabletop battles are `PendingItem`s: parties report, agreement resolves,
disagreement goes to the GM. The ruleset only supplies the reaction (`onPendingResolved`). The same mechanism serves any
"something happens outside the app" step in another setting.

**Determinism.** No clock or randomness in rules: `ctx.now()`, and `ctx.random()` seeded from (campaign seed, version),
which is reproducible and does not repeat after a restart. All state collections are sorted (`Sorted`), because
`Set.of`/`Map.of` iteration order is randomised per JVM. `GameState.fingerprint()` lets tests prove replay equals live.

**Transactions.** One `submit()`/`tick()` is one append of all resulting events. If anything throws midway, the
`Campaign` instance marks itself broken and is rebuilt from the store (game-app evicts it).

**Spring boundary.** Enforced by the build, not by convention. The kernel has ports; `game-app` implements them
(`JdbcEventStore`) and drives them (`CampaignRuntime` serialises per campaign; `DeadlineScheduler` calls `tick()`).

**Jackson 2 on purpose.** Spring Boot 4 defaults to Jackson 3. `game-app` never gives Spring MVC our mapper (controllers
exchange JSON strings through `CampaignJson`), so the two coexist, and a later move to Jackson 3 touches one class.

## Extension seams that already exist

| Want | Where |
|---|---|
| Players take turns instead of simultaneous orders | implement `PhaseStrategy`, add the case in `forMode` |
| Fog of war | `RulesetRegistry.viewPolicy(...)` |
| Ruleset-specific facts | `registry.event(Type.class, reducer)` (see `IncomeCollected`) |
| New persistence | implement `EventStore` |
| New language or setting vocabulary | one `.properties` file |
| Second ruleset | new module + `META-INF/services` entry; run `Conformance.verify` |
