package dev.campaign.app;

import dev.campaign.kernel.api.CampaignConfig;
import dev.campaign.kernel.api.Command;

/** Request/response bodies. Everything else on the wire is a kernel record (views, events, results). */
public final class Api {
    private Api() {}

    public record CreateRequest(String rulesetId, CampaignConfig config) {}

    public record CreateResponse(String id) {}

    public record SubmitRequest(int turn, String phase, Command command) {}

    public record Fingerprint(String value) {}
}
