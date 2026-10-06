package dev.campaign.testkit;

import dev.campaign.kernel.api.CampaignConfig;
import dev.campaign.kernel.api.Ruleset;
import dev.campaign.kernel.event.StoredEvent;
import dev.campaign.kernel.event.ViewEvent;
import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.model.Actor;
import dev.campaign.kernel.model.Participant;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarantees every ruleset must keep, checked against a scripted playthrough of that ruleset:
 * 1. determinism: the same seed and script produce identical events;
 * 2. replay: rebuilding from the stored events yields the identical state;
 * 3. no leaks: nobody receives an event in full unless its audience includes them.
 * Point it at any new ruleset with a short script (which must call create() itself) and these come for free.
 */
public final class Conformance {
    private Conformance() {}

    public static void verify(Ruleset ruleset, CampaignConfig config, Consumer<CampaignDriver> script) {
        // The script is responsible for creating the campaign (it owns the config), then playing it.
        InProcessDriver first = new InProcessDriver(ruleset);
        script.accept(first);

        InProcessDriver second = new InProcessDriver(ruleset);
        script.accept(second);

        List<StoredEvent> log = first.store().load(InProcessDriver.CAMPAIGN);
        assertEquals(log, second.store().load(InProcessDriver.CAMPAIGN), "same seed + same script must give identical events");

        String live = first.fingerprint();
        first.restart();
        assertEquals(live, first.fingerprint(), "replaying the log must reproduce the live state");

        Map<Long, StoredEvent> bySeq = new TreeMap<>();
        log.forEach(e -> bySeq.put(e.seq(), e));
        for (Participant p : config.participants()) {
            Actor viewer = Actor.asPlayer(p.player().value());
            FactionId faction = p.faction();
            for (ViewEvent seen : first.events(viewer, 0)) {
                StoredEvent stored = bySeq.get(seen.seq());
                if (!stored.audience().canSee(viewer, faction)) {
                    assertEquals(stored.redacted(), seen.event(),
                            "event " + seen.seq() + " leaked to " + p.player() + " outside its audience");
                }
            }
        }
        assertTrue(first.events(Actor.asGm("gm"), 0).size() == log.size(), "the GM must see every event");
    }
}
