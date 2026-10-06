package dev.campaign.testkit;

import dev.campaign.kernel.api.CampaignConfig;
import dev.campaign.kernel.api.Command;
import dev.campaign.kernel.api.Ruleset;
import dev.campaign.kernel.engine.Campaign;
import dev.campaign.kernel.engine.PlayerView;
import dev.campaign.kernel.engine.SubmitResult;
import dev.campaign.kernel.event.ViewEvent;
import dev.campaign.kernel.id.CampaignId;
import dev.campaign.kernel.model.Actor;
import dev.campaign.kernel.store.InMemoryEventStore;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class InProcessDriver implements CampaignDriver {
    public static final CampaignId CAMPAIGN = CampaignId.of("test-campaign");

    private final Ruleset ruleset;
    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T09:00:00Z"));
    private final InMemoryEventStore store = new InMemoryEventStore();
    private Campaign campaign;

    public InProcessDriver(Ruleset ruleset) {
        this.ruleset = ruleset;
    }

    @Override
    public void create(CampaignConfig config) {
        campaign = Campaign.create(CAMPAIGN, ruleset, config, store, clock);
    }

    @Override
    public SubmitResult submit(Actor actor, int turn, String phase, Command command) {
        return campaign.submit(actor, turn, phase, command);
    }

    @Override
    public SubmitResult tick() {
        return campaign.tick();
    }

    @Override
    public PlayerView view(Actor actor) {
        return campaign.viewFor(actor);
    }

    @Override
    public List<ViewEvent> events(Actor actor, long afterSeq) {
        return campaign.eventsFor(actor, afterSeq);
    }

    @Override
    public String fingerprint() {
        return campaign.fingerprint();
    }

    @Override
    public void advanceClock(Duration duration) {
        clock.advance(duration);
    }

    @Override
    public void restart() {
        campaign = Campaign.load(CAMPAIGN, ruleset, store, clock);
    }

    public InMemoryEventStore store() {
        return store;
    }
}
