package dev.campaign.kernel.api;

import dev.campaign.kernel.id.EntityId;
import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.model.Actor;
import dev.campaign.kernel.state.StateReader;

import java.time.Instant;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * What a handler or hook may see: read-only state, who is acting, the time, and deterministic randomness.
 * There is deliberately no way to change anything from here; handlers return events instead.
 */
public final class Ctx {
    private final StateReader state;
    private final Actor actor;
    private final PhaseDef phase;
    private final Instant now;
    private RandomGenerator random;
    private int idOffset;

    public Ctx(StateReader state, Actor actor, PhaseDef phase, Instant now) {
        this.state = state;
        this.actor = actor;
        this.phase = phase;
        this.now = now;
    }

    public StateReader state() {
        return state;
    }

    /** Null for system-initiated work (hooks, resolution, deadlines). */
    public Actor actor() {
        return actor;
    }

    public Optional<FactionId> faction() {
        if (actor == null || actor.isGm()) return Optional.empty();
        return state.factionOf(actor.player());
    }

    public PhaseDef phase() {
        return phase;
    }

    public Instant now() {
        return now;
    }

    /**
     * Seeded from (campaign seed, current version), so it is reproducible and does not repeat after a restart.
     * Never use Math.random(), ThreadLocalRandom or UUID.randomUUID() in rules.
     */
    public RandomGenerator random() {
        if (random == null) {
            random = new SplittableRandom(state.seed() ^ (state.version() * 0x9E3779B97F4A7C15L));
        }
        return random;
    }

    /** A fresh entity id such as "warband-3". Unique for the campaign; safe to call several times per decision. */
    public EntityId newEntityId(String prefix) {
        return EntityId.of(prefix + "-" + (state.entitySerial() + 1 + idOffset++));
    }
}
