package dev.campaign.scenario;

import dev.campaign.kernel.engine.CommandSchema;
import dev.campaign.kernel.event.ViewEvent;
import dev.campaign.kernel.theme.Narrator;
import dev.campaign.rules.tinyrealms.MoveUnit;
import dev.campaign.rules.tinyrealms.TinyRealms;
import dev.campaign.testkit.Conformance;
import dev.campaign.testkit.InProcessDriver;
import dev.campaign.theme.tinyrealms.TinyRealmsThemes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InProcessMiniCampaignTest {

    @Test
    @DisplayName("A three-turn campaign plays through the kernel with no framework at all")
    void playsTheMiniCampaign() {
        MiniCampaignScenario.play(new InProcessDriver(new TinyRealms()));
    }

    @Test
    @DisplayName("The ruleset keeps the framework's guarantees: determinism, replay, no leaks")
    void rulesetConformance() {
        Conformance.verify(new TinyRealms(), MiniCampaignScenario.config(), MiniCampaignScenario::play);
    }

    @Test
    @DisplayName("The same events read as a fantasy chronicle or a freight manifest, depending on the theme")
    void sameEventsReadDifferentlyInTwoThemes() throws IOException {
        InProcessDriver driver = new InProcessDriver(new TinyRealms());
        MiniCampaignScenario.play(driver);
        List<ViewEvent> log = driver.events(MiniCampaignScenario.GM, 0);

        String fantasy = new Narrator(TinyRealmsThemes.fantasy()).transcript(log);
        String space = new Narrator(TinyRealmsThemes.spacefaring()).transcript(log);

        assertTrue(fantasy.contains("marches"), fantasy);
        assertTrue(space.contains("jumps"), space);
        assertFalse(space.contains("marches"));
        assertFalse(fantasy.contains("jumps"));

        Path out = Path.of("target", "transcripts");
        Files.createDirectories(out);
        Files.writeString(out.resolve("campaign-fantasy.md"), "# Campaign transcript (fantasy)\n" + fantasy);
        Files.writeString(out.resolve("campaign-spacefaring.md"), "# Campaign transcript (spacefaring)\n" + space);
    }

    @Test
    @DisplayName("A command's UI prompt flow is derived from its record, with no per-command UI code")
    void commandSchemaComesFromTheRecord() {
        List<CommandSchema.Param> params = CommandSchema.params(MoveUnit.class);
        assertEquals(2, params.size());
        assertEquals(new CommandSchema.Param("unit", CommandSchema.ParamType.ENTITY, false, "mobile"), params.get(0));
        assertEquals(new CommandSchema.Param("path", CommandSchema.ParamType.LOCATION, true, null), params.get(1));
    }
}
