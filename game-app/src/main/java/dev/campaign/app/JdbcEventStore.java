package dev.campaign.app;

import dev.campaign.json.CampaignJson;
import dev.campaign.kernel.event.StoredEvent;
import dev.campaign.kernel.id.CampaignId;
import dev.campaign.kernel.store.ConcurrencyException;
import dev.campaign.kernel.store.EventStore;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** EventStore on a single table. Optimistic concurrency: check the last seq, and let the primary key be the backstop. */
public class JdbcEventStore implements EventStore {
    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final CampaignJson json;

    public JdbcEventStore(JdbcClient jdbc, TransactionTemplate transactions, CampaignJson json) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.json = json;
    }

    @Override
    public List<StoredEvent> load(CampaignId id, long afterSeq) {
        return jdbc.sql("select json from campaign_event where campaign_id = :id and seq > :after order by seq")
                .param("id", id.value())
                .param("after", afterSeq)
                .query(String.class)
                .list()
                .stream()
                .map(text -> json.read(text, StoredEvent.class))
                .toList();
    }

    @Override
    public void append(CampaignId id, long expectedLastSeq, List<StoredEvent> events) {
        try {
            transactions.executeWithoutResult(status -> {
                long last = jdbc.sql("select coalesce(max(seq), 0) from campaign_event where campaign_id = :id")
                        .param("id", id.value())
                        .query(Long.class)
                        .single();
                if (last != expectedLastSeq) {
                    throw new ConcurrencyException("Expected last seq " + expectedLastSeq + " but found " + last + " for " + id);
                }
                for (StoredEvent event : events) {
                    jdbc.sql("insert into campaign_event (campaign_id, seq, at, json) values (:id, :seq, :at, :json)")
                            .param("id", id.value())
                            .param("seq", event.seq())
                            .param("at", Timestamp.from(event.at()))
                            .param("json", json.write(event))
                            .update();
                }
            });
        } catch (DuplicateKeyException e) {
            throw new ConcurrencyException("Concurrent append to " + id);
        }
    }

    @Override
    public Set<CampaignId> campaignIds() {
        Set<CampaignId> ids = new TreeSet<>();
        jdbc.sql("select distinct campaign_id from campaign_event").query(String.class).list()
                .forEach(value -> ids.add(CampaignId.of(value)));
        return ids;
    }
}
