package dev.campaign.kernel.store;

import dev.campaign.kernel.event.StoredEvent;
import dev.campaign.kernel.id.CampaignId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class InMemoryEventStore implements EventStore {
    private final Map<CampaignId, List<StoredEvent>> logs = new TreeMap<>();

    @Override
    public synchronized List<StoredEvent> load(CampaignId id, long afterSeq) {
        List<StoredEvent> log = logs.getOrDefault(id, List.of());
        List<StoredEvent> out = new ArrayList<>();
        for (StoredEvent e : log) if (e.seq() > afterSeq) out.add(e);
        return out;
    }

    @Override
    public synchronized void append(CampaignId id, long expectedLastSeq, List<StoredEvent> events) {
        List<StoredEvent> log = logs.computeIfAbsent(id, k -> new ArrayList<>());
        long last = log.isEmpty() ? 0 : log.get(log.size() - 1).seq();
        if (last != expectedLastSeq) {
            throw new ConcurrencyException("Expected last seq " + expectedLastSeq + " but found " + last + " for " + id);
        }
        log.addAll(events);
    }

    @Override
    public synchronized Set<CampaignId> campaignIds() {
        return new TreeSet<>(logs.keySet());
    }
}
