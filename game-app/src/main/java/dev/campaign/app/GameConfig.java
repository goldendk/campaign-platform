package dev.campaign.app;

import dev.campaign.json.CampaignJson;
import dev.campaign.kernel.ruleset.RulesetCatalog;
import dev.campaign.kernel.store.EventStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/** Wires the framework-free parts together. */
@Configuration
public class GameConfig {

    @Bean
    Rulesets rulesets() {
        return new Rulesets(RulesetCatalog.discover());
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    CampaignJson campaignJson(Rulesets rulesets) {
        return new CampaignJson(rulesets.byId().values());
    }

    @Bean
    EventStore eventStore(JdbcClient jdbc, TransactionTemplate transactions, CampaignJson json) {
        return new JdbcEventStore(jdbc, transactions, json);
    }
}
