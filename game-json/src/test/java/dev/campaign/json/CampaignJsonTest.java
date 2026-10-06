package dev.campaign.json;

import dev.campaign.kernel.api.Command;
import dev.campaign.kernel.engine.PlayerView;
import dev.campaign.kernel.event.StoredEvent;
import dev.campaign.kernel.id.EntityId;
import dev.campaign.kernel.id.LocationId;
import dev.campaign.rules.tinyrealms.MoveUnit;
import dev.campaign.rules.tinyrealms.TinyRealms;
import dev.campaign.scenario.MiniCampaignScenario;
import dev.campaign.testkit.InProcessDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignJsonTest {
    private final CampaignJson json = new CampaignJson(List.of(new TinyRealms()));

    @Test
    @DisplayName("Every stored event of the mini-campaign survives a JSON round trip unchanged")
    void storedEventsRoundTrip() {
        InProcessDriver driver = new InProcessDriver(new TinyRealms());
        MiniCampaignScenario.play(driver);
        List<StoredEvent> log = driver.store().load(InProcessDriver.CAMPAIGN);
        assertTrue(log.size() > 50);
        for (StoredEvent event : log) {
            assertEquals(event, json.read(json.write(event), StoredEvent.class), "event " + event.seq());
        }
    }

    @Test
    @DisplayName("Views round trip, including id-keyed maps")
    void viewsRoundTrip() {
        InProcessDriver driver = new InProcessDriver(new TinyRealms());
        MiniCampaignScenario.play(driver);
        PlayerView view = driver.view(MiniCampaignScenario.GM);
        assertEquals(view, json.read(json.write(view), PlayerView.class));
    }

    @Test
    @DisplayName("Commands carry a ruleset-qualified type name and come back as the same record")
    void commandsKeepTheirType() {
        Command command = new MoveUnit(EntityId.of("red-w1"), List.of(LocationId.of("wood")));
        String text = json.write(command);
        assertTrue(text.contains("tiny-realms:MoveUnit"), text);
        assertEquals(command, json.read(text, Command.class));
    }
}
