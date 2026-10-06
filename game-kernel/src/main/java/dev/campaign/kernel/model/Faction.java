package dev.campaign.kernel.model;

import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.id.PlayerId;
import dev.campaign.kernel.util.Sorted;

import java.util.Map;
import java.util.TreeMap;

public record Faction(FactionId id, PlayerId player, String name, Map<String, Long> resources) {
    public Faction {
        resources = Sorted.map(resources);
    }

    public long resource(String name) {
        return resources.getOrDefault(name, 0L);
    }

    public Faction adjusted(String resource, long delta) {
        Map<String, Long> copy = new TreeMap<>(resources);
        copy.merge(resource, delta, Long::sum);
        return new Faction(id, player, name, copy);
    }
}
