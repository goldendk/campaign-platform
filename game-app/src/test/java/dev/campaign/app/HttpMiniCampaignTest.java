package dev.campaign.app;

import dev.campaign.json.CampaignJson;
import dev.campaign.scenario.MiniCampaignScenario;
import dev.campaign.testkit.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HttpMiniCampaignTest {

    @TestConfiguration
    static class TestClock {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2026-01-01T09:00:00Z"));
        }
    }

    @Value("${local.server.port}")
    int port;

    @Autowired MutableClock clock;
    @Autowired CampaignRuntime runtime;
    @Autowired CampaignJson json;

    @Test
    @DisplayName("The identical mini-campaign plays through REST + JDBC, including restarts from the stored log")
    void playsTheSameStoryOverHttp() {
        MiniCampaignScenario.play(new HttpCampaignDriver(port, json, clock, runtime));
    }
}
