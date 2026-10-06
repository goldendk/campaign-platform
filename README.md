# campaign-platform

A backend framework for asynchronous, turn-based campaign systems that sit on top of a tabletop game
(first target: Warhammer Fantasy *Mighty Empires*). The kernel knows nothing about armies or tiles; a **ruleset
module** supplies the setting, and a **theme** supplies the words. Moving an army and sailing a cargo ship between
solar systems are the same command with different vocabulary.

Java 21, Maven multi-module, Spring Boot only in `game-app`.

## Start here

Read `scenario-tests/src/main/java/dev/campaign/scenario/MiniCampaignScenario.java` top to bottom. It plays a
three-turn campaign and every step says which framework concept it demonstrates. It is the acceptance test and the
usage manual at once, and it runs unchanged against the kernel directly and against the REST API.

## Modules

| Module | Spring? | What it is |
|---|---|---|
| `game-kernel` | no | Turn machine, command pipeline, events, views, ports (`EventStore`), theme/narrator |
| `game-json` | no | The only JSON code (Jackson 2). Kernel records are serialised as they are |
| `game-testkit` | no | `CampaignDriver`, in-process driver, fake clock, ruleset conformance suite |
| `rules-tiny-realms` | no | A deliberately tiny ruleset: the reference example |
| `theme-tiny-realms` | no | Two vocabularies for it: fantasy and spacefaring |
| `scenario-tests` | no | The mini-campaign, written once, run through any `CampaignDriver` |
| `game-app` | **yes** | REST controller, JDBC event store, deadline timer, wiring. No game rules |

The build fails if Spring appears on the classpath of any module other than `game-app` (enforcer rule in the
parent `pom.xml`).

## Build and run

```
mvn verify                                  # all tests, incl. the REST + JDBC run of the scenario
mvn -pl game-app spring-boot:run            # dev server on H2
```

The in-process scenario also writes two transcripts of the same campaign, one per theme, to
`scenario-tests/target/transcripts/`.

## Add a command (the whole job)

1. A record for the parameters and a handler, in one file (see `MoveUnit.java`):

```java
public record Recruit(LocationId at) implements Command {
    public static final class Handler implements CommandHandler<Recruit> {
        public Validation validate(Ctx ctx, Recruit c) { /* return Validation.fail("code", "arg", "value") */ }
        public List<Emission> decide(Ctx ctx, Recruit c) { /* return events */ }
    }
}
```

2. One line in your ruleset's `register`: `registry.command(CommandSpec.staged(Recruit.class, new Recruit.Handler(), 5, "orders"));`
3. Theme strings for the new reason codes (`reason.recruit.too_poor=...`).

No controller, DTO, mapper, table or UI form. The record's component types drive the generated prompt flow
(`CommandSchema`), JSON serialisation is derived from the record, and the handler's events are what gets stored.

## Add a setting

1. A module with a class implementing `Ruleset` (phases, commands, hooks, victory, `setup`), plus
   `META-INF/services/dev.campaign.kernel.api.Ruleset`.
2. A theme module: one `.properties` file per vocabulary/language.
3. Add the ruleset as a dependency of `game-app`. It is discovered with `ServiceLoader`.
4. Point `Conformance.verify(...)` at it with a short script: determinism, replay and no-leak checks come free.

## Rules that keep the design honest

* **Events are the only thing that changes state**, and the only thing stored. Handlers return events; they never mutate.
* **Handlers and hooks are pure**: no clock, no `Math.random()`, no `UUID`, no I/O. Use `ctx.now()` and `ctx.random()`.
* **No `Set.of` / `Map.of` in anything stored or iterated**: their order differs per JVM run. Compact constructors copy
  through `Sorted.set/map`; do the same in new records.
* **Spring stays in `game-app`.** If you want something framework-shaped in the kernel, define a port and adapt it there.

## Status: what has and has not been run

Compiled and executed under JDK 21 (using stand-ins for the JUnit annotations): `game-kernel`, `rules-tiny-realms`,
`theme-tiny-realms`, `game-testkit`, `scenario-tests`. All four scenario tests pass: the campaign plays in-process, the
conformance suite holds, both theme transcripts render, the command schema is derived from the record.

**Not compiled or run** (no network access to fetch dependencies): `game-json`, `game-app`, and the Maven poms
themselves. Check first: `spring-boot.version` (set to 4.1.0; use the latest 4.1.x), `jackson2.version`, and the
Spring Boot 4 starter names (`spring-boot-starter-webmvc`, `-jdbc`). Expect small fixes there, not design changes.

## Not built yet

* `SEQUENTIAL` phase mode (the seam exists: `PhaseStrategy`, selectable per phase via `CampaignConfig.phaseModes`).
* Generic event reactions ("when X happens, do Y"); today rulesets use phase hooks and pending-item hooks.
* Fog of war (`ViewPolicy` seam exists, default shows everything). Phases as YAML/JSON instead of Java.
* Authentication (headers are trusted), notifications, order revision before locking in.
* Orders are validated against the current world, not against the player's earlier staged orders; conflicts are
  settled at resolution time (a rejected order produces `OrderRejected`).
