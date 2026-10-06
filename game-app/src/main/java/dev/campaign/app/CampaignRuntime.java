package dev.campaign.app;

import dev.campaign.kernel.api.CampaignConfig;
import dev.campaign.kernel.api.Ruleset;
import dev.campaign.kernel.engine.Campaign;
import dev.campaign.kernel.id.CampaignId;
import dev.campaign.kernel.store.EventStore;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Keeps loaded campaigns in memory and serialises access per campaign, which is the concurrency model the kernel
 * expects. Any failure evicts the instance, so the next call rebuilds it from the stored events.
 */
@Service
public class CampaignRuntime {
    private final Rulesets rulesets;
    private final EventStore store;
    private final Clock clock;
    private final Map<CampaignId, Campaign> cache = new ConcurrentHashMap<>();
    private final Map<CampaignId, Object> locks = new ConcurrentHashMap<>();

    public CampaignRuntime(Rulesets rulesets, EventStore store, Clock clock) {
        this.rulesets = rulesets;
        this.store = store;
        this.clock = clock;
    }

    public CampaignId create(String rulesetId, CampaignConfig config) {
        Ruleset ruleset = rulesets.byId().get(rulesetId);
        if (ruleset == null) throw new IllegalArgumentException("Unknown ruleset: " + rulesetId);
        CampaignId id = CampaignId.of(UUID.randomUUID().toString());
        synchronized (lock(id)) {
            cache.put(id, Campaign.create(id, ruleset, config, store, clock));
        }
        return id;
    }

    public <T> T with(CampaignId id, Function<Campaign, T> action) {
        synchronized (lock(id)) {
            Campaign campaign = cache.get(id);
            if (campaign == null) {
                campaign = Campaign.load(id, rulesets.byId(), store, clock);
                cache.put(id, campaign);
            }
            try {
                return action.apply(campaign);
            } catch (RuntimeException e) {
                cache.remove(id);
                throw e;
            }
        }
    }

    /** Drop the in-memory instance; the next call replays it from the store. Used by tests to simulate a restart. */
    public void evict(CampaignId id) {
        cache.remove(id);
    }

    public void tickAll() {
        for (CampaignId id : store.campaignIds()) with(id, Campaign::tick);
    }

    private Object lock(CampaignId id) {
        return locks.computeIfAbsent(id, k -> new Object());
    }
}
