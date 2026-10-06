package dev.campaign.testkit;

import dev.campaign.kernel.api.CampaignConfig;
import dev.campaign.kernel.api.Command;
import dev.campaign.kernel.engine.PlayerView;
import dev.campaign.kernel.engine.SubmitResult;
import dev.campaign.kernel.event.ViewEvent;
import dev.campaign.kernel.model.Actor;

import java.time.Duration;
import java.util.List;

/**
 * The one interface a scenario is written against. InProcessDriver calls the kernel directly; the HttpDriver in
 * game-app's tests calls the REST API. The same story runs through both, which proves that the adapter layer
 * adds no behaviour of its own.
 */
public interface CampaignDriver {
    void create(CampaignConfig config);

    SubmitResult submit(Actor actor, int turn, String phase, Command command);

    /** Submit against whatever turn/phase the actor currently sees. */
    default SubmitResult submit(Actor actor, Command command) {
        PlayerView view = view(actor);
        return submit(actor, view.turn(), view.phase(), command);
    }

    /** What the scheduler does periodically: enforce deadlines. */
    SubmitResult tick();

    PlayerView view(Actor actor);

    List<ViewEvent> events(Actor actor, long afterSeq);

    /** Digest of the full state, for determinism checks. */
    String fingerprint();

    void advanceClock(Duration duration);

    /** Throw away in-memory state and rebuild it from the stored events, as after a server restart. */
    void restart();
}
