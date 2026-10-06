package dev.campaign.kernel.store;

import dev.campaign.kernel.event.StoredEvent;
import dev.campaign.kernel.id.CampaignId;

import java.util.List;
import java.util.Set;

/**
 * Port for persistence. The kernel only needs an append-only log per campaign with an optimistic check.
 * Implementations: InMemoryEventStore (here), JdbcEventStore (game-app).
 */
public interface EventStore {
    List<StoredEvent> load(CampaignId id, long afterSeq);

    /** Append atomically, or throw ConcurrencyException if the stored last seq is not expectedLastSeq. */
    void append(CampaignId id, long expectedLastSeq, List<StoredEvent> events);

    Set<CampaignId> campaignIds();

    default List<StoredEvent> load(CampaignId id) {
        return load(id, 0);
    }
}
