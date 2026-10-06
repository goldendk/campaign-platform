package dev.campaign.kernel.engine;

import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.id.PlayerId;
import dev.campaign.kernel.util.Sorted;

import java.util.Map;

/** resources is null when the viewer may not see them (only the owner and the GM can). */
public record FactionView(FactionId id, String name, PlayerId player, Map<String, Long> resources) {
    public FactionView {
        resources = resources == null ? null : Sorted.map(resources);
    }
}
