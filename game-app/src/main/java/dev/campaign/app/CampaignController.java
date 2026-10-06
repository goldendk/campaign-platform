package dev.campaign.app;

import dev.campaign.json.CampaignJson;
import dev.campaign.kernel.engine.Campaign;
import dev.campaign.kernel.id.CampaignId;
import dev.campaign.kernel.model.Actor;
import dev.campaign.kernel.model.Role;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

/**
 * Thin HTTP adapter: decode, call the kernel, encode. A rejected command is a normal 200 with accepted=false and
 * reasons, not an HTTP error. Bodies are JSON strings produced by CampaignJson, so the kernel records go over the
 * wire as they are.
 *
 * TODO authentication: X-Player / X-Role are trusted as-is. Replace with Spring Security and derive the Actor
 * from the authenticated principal.
 */
@RestController
@RequestMapping("/campaigns")
public class CampaignController {
    private final CampaignRuntime runtime;
    private final CampaignJson json;

    CampaignController(CampaignRuntime runtime, CampaignJson json) {
        this.runtime = runtime;
        this.json = json;
    }

    @PostMapping
    ResponseEntity<String> create(@RequestBody String body) {
        Api.CreateRequest request = json.read(body, Api.CreateRequest.class);
        CampaignId id = runtime.create(request.rulesetId(), request.config());
        return ok(new Api.CreateResponse(id.value()));
    }

    @PostMapping("/{id}/commands")
    ResponseEntity<String> submit(@PathVariable String id,
                                  @RequestHeader("X-Player") String player,
                                  @RequestHeader(value = "X-Role", defaultValue = "PLAYER") String role,
                                  @RequestBody String body) {
        Api.SubmitRequest request = json.read(body, Api.SubmitRequest.class);
        Actor actor = actor(player, role);
        return ok(runtime.with(CampaignId.of(id), c -> c.submit(actor, request.turn(), request.phase(), request.command())));
    }

    @PostMapping("/{id}/tick")
    ResponseEntity<String> tick(@PathVariable String id) {
        return ok(runtime.with(CampaignId.of(id), Campaign::tick));
    }

    @GetMapping("/{id}/view")
    ResponseEntity<String> view(@PathVariable String id,
                                @RequestHeader("X-Player") String player,
                                @RequestHeader(value = "X-Role", defaultValue = "PLAYER") String role) {
        Actor actor = actor(player, role);
        return ok(runtime.with(CampaignId.of(id), c -> c.viewFor(actor)));
    }

    @GetMapping("/{id}/events")
    ResponseEntity<String> events(@PathVariable String id,
                                  @RequestHeader("X-Player") String player,
                                  @RequestHeader(value = "X-Role", defaultValue = "PLAYER") String role,
                                  @RequestParam(defaultValue = "0") long after) {
        Actor actor = actor(player, role);
        return ok(runtime.with(CampaignId.of(id), c -> c.eventsFor(actor, after)));
    }

    /** The command catalogue: what a generic UI needs to build its prompt flows. */
    @GetMapping("/{id}/commands")
    ResponseEntity<String> commands(@PathVariable String id) {
        return ok(runtime.with(CampaignId.of(id), Campaign::commandSchemas));
    }

    @GetMapping("/{id}/fingerprint")
    ResponseEntity<String> fingerprint(@PathVariable String id) {
        return ok(new Api.Fingerprint(runtime.with(CampaignId.of(id), Campaign::fingerprint)));
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<String> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    private ResponseEntity<String> ok(Object body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json.write(body));
    }

    private static Actor actor(String player, String role) {
        return new Actor(dev.campaign.kernel.id.PlayerId.of(player), Role.valueOf(role));
    }
}
