package dev.campaign.app;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.campaign.json.CampaignJson;
import dev.campaign.kernel.api.CampaignConfig;
import dev.campaign.kernel.api.Command;
import dev.campaign.kernel.engine.PlayerView;
import dev.campaign.kernel.engine.SubmitResult;
import dev.campaign.kernel.event.ViewEvent;
import dev.campaign.kernel.id.CampaignId;
import dev.campaign.kernel.model.Actor;
import dev.campaign.rules.tinyrealms.TinyRealms;
import dev.campaign.testkit.CampaignDriver;
import dev.campaign.testkit.MutableClock;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/** Drives the real REST API. Same CampaignDriver interface as the in-process driver, so the same story runs. */
final class HttpCampaignDriver implements CampaignDriver {
    private final RestClient http;
    private final CampaignJson json;
    private final MutableClock clock;
    private final CampaignRuntime runtime;
    private String id;

    HttpCampaignDriver(int port, CampaignJson json, MutableClock clock, CampaignRuntime runtime) {
        this.http = RestClient.create("http://localhost:" + port);
        this.json = json;
        this.clock = clock;
        this.runtime = runtime;
    }

    @Override
    public void create(CampaignConfig config) {
        String body = http.post().uri("/campaigns")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json.write(new Api.CreateRequest(TinyRealms.ID, config)))
                .retrieve().body(String.class);
        id = json.read(body, Api.CreateResponse.class).id();
    }

    @Override
    public SubmitResult submit(Actor actor, int turn, String phase, Command command) {
        String body = http.post().uri("/campaigns/{id}/commands", id)
                .header("X-Player", actor.player().value())
                .header("X-Role", actor.role().name())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json.write(new Api.SubmitRequest(turn, phase, command)))
                .retrieve().body(String.class);
        return json.read(body, SubmitResult.class);
    }

    @Override
    public SubmitResult tick() {
        return json.read(http.post().uri("/campaigns/{id}/tick", id).retrieve().body(String.class), SubmitResult.class);
    }

    @Override
    public PlayerView view(Actor actor) {
        String body = http.get().uri("/campaigns/{id}/view", id)
                .header("X-Player", actor.player().value())
                .header("X-Role", actor.role().name())
                .retrieve().body(String.class);
        return json.read(body, PlayerView.class);
    }

    @Override
    public List<ViewEvent> events(Actor actor, long afterSeq) {
        String body = http.get().uri("/campaigns/{id}/events?after={after}", id, afterSeq)
                .header("X-Player", actor.player().value())
                .header("X-Role", actor.role().name())
                .retrieve().body(String.class);
        return json.read(body, new TypeReference<List<ViewEvent>>() {});
    }

    @Override
    public String fingerprint() {
        String body = http.get().uri("/campaigns/{id}/fingerprint", id).retrieve().body(String.class);
        return json.read(body, Api.Fingerprint.class).value();
    }

    @Override
    public void advanceClock(Duration duration) {
        clock.advance(duration);
    }

    /** Evicts the in-memory campaign; the next request replays it from the JDBC event store. */
    @Override
    public void restart() {
        runtime.evict(CampaignId.of(id));
    }
}
