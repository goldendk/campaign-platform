package dev.campaign.scenario;

import dev.campaign.kernel.api.CampaignConfig;
import dev.campaign.kernel.command.CoreCommands.Adjudicate;
import dev.campaign.kernel.command.CoreCommands.LockIn;
import dev.campaign.kernel.command.CoreCommands.ReportOutcome;
import dev.campaign.kernel.engine.PlayerView;
import dev.campaign.kernel.engine.SubmitResult;
import dev.campaign.kernel.event.CoreEvent.OrderStaged;
import dev.campaign.kernel.event.CoreEvent.OrderStagedHidden;
import dev.campaign.kernel.event.ViewEvent;
import dev.campaign.kernel.id.EntityId;
import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.id.LocationId;
import dev.campaign.kernel.model.Actor;
import dev.campaign.kernel.model.Entity;
import dev.campaign.kernel.model.Participant;
import dev.campaign.kernel.model.PendingItem;
import dev.campaign.rules.tinyrealms.MoveUnit;
import dev.campaign.rules.tinyrealms.PlaceTile;
import dev.campaign.rules.tinyrealms.Recruit;
import dev.campaign.testkit.CampaignDriver;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A three-turn campaign played through a CampaignDriver. It is both the acceptance test and the usage manual:
 * read it top to bottom to see every concept of the framework in action. Each step says what it demonstrates.
 *
 * Map:  red-keep - wood - ford - hill - blue-keep      (mine joins wood and hill)
 *
 *   Turn 1  sealed orders, privacy, rejection, deadline auto-lock, income
 *   Turn 2  stale commands, simultaneous move into the same ford, a tabletop battle with disputed reports
 *   Turn 3  the map grows, victory condition, campaign ends
 */
public final class MiniCampaignScenario {
    private MiniCampaignScenario() {}

    public static final Actor ALICE = Actor.asPlayer("alice");
    public static final Actor BOB = Actor.asPlayer("bob");
    public static final Actor GM = Actor.asGm("gm");

    public static final FactionId RED = FactionId.of("red");
    public static final FactionId BLUE = FactionId.of("blue");

    static final LocationId RED_KEEP = LocationId.of("red-keep");
    static final LocationId WOOD = LocationId.of("wood");
    static final LocationId FORD = LocationId.of("ford");
    static final LocationId MINE = LocationId.of("mine");
    static final LocationId HILL = LocationId.of("hill");
    static final EntityId RED_W1 = EntityId.of("red-w1");
    static final EntityId BLUE_W1 = EntityId.of("blue-w1");

    public static CampaignConfig config() {
        return CampaignConfig.of(42L, Participant.of("alice", "red", "Alice"), Participant.of("bob", "blue", "Bob"));
    }

    public static void play(CampaignDriver d) {
        d.create(config());
        turnOne(d);
        replayMatchesLive(d, "after turn 1");
        EntityId recruit = turnTwo(d);
        replayMatchesLive(d, "after turn 2");
        turnThree(d, recruit);
        replayMatchesLive(d, "after the campaign ended");
    }

    // ------------------------------------------------------------------ turn 1

    private static void turnOne(CampaignDriver d) {
        // The opening view: the world exists, it is turn 1, orders phase. Opponents' treasuries are hidden.
        PlayerView alice = d.view(ALICE);
        assertEquals(1, alice.turn());
        assertEquals("orders", alice.phase());
        assertEquals(6, alice.locations().size());
        assertEquals(3L, alice.faction(RED).resources().get("gold"));
        assertNull(alice.faction(BLUE).resources(), "an opponent's treasury is not visible");

        // Orders are STAGED commands: accepted now, executed when the phase resolves.
        accepted(d.submit(ALICE, new MoveUnit(RED_W1, List.of(WOOD))));
        accepted(d.submit(ALICE, new Recruit(RED_KEEP)));

        // Illegal orders are refused with a code + arguments (themes turn them into prose). Nothing is stored.
        rejected(d.submit(ALICE, new MoveUnit(RED_W1, List.of(WOOD, FORD, HILL))), "move.too_far");

        accepted(d.submit(BOB, new MoveUnit(BLUE_W1, List.of(HILL))));

        // PRIVACY: Alice learns that Bob issued orders, never what they are.
        List<ViewEvent> seenByAlice = d.events(ALICE, 0);
        assertTrue(seenByAlice.stream().anyMatch(e -> e.event() instanceof OrderStagedHidden h && h.faction().equals(BLUE)));
        assertTrue(seenByAlice.stream().noneMatch(e -> e.event() instanceof OrderStaged s && s.faction().equals(BLUE)));
        assertNull(d.view(ALICE).visibleOrders().get(BLUE));
        assertEquals(2, d.view(ALICE).visibleOrders().get(RED).size());
        assertEquals(1, d.view(GM).visibleOrders().get(BLUE).size(), "the GM sees every faction's orders");

        // Alice seals her orders. Bob has not, so the phase stays open and Alice can no longer add orders.
        accepted(d.submit(ALICE, new LockIn()));
        assertEquals("orders", d.view(BOB).phase());
        rejected(d.submit(ALICE, new Recruit(RED_KEEP)), "orders.already_locked");

        // DEADLINE: Bob never locks in. When the 24h deadline passes, the scheduler's tick() locks him in
        // automatically, the orders resolve, upkeep runs and turn 2 begins.
        d.advanceClock(Duration.ofHours(25));
        accepted(d.tick());

        PlayerView t2 = d.view(ALICE);
        assertEquals(2, t2.turn());
        assertEquals("orders", t2.phase());
        assertEquals(RED, t2.location(WOOD).owner(), "red moved into wood unopposed and claimed it");
        assertEquals(BLUE, t2.location(HILL).owner());
        assertEquals(WOOD, t2.entity(RED_W1).orElseThrow().at());
        // Gold: 3 start - 2 recruit + 2 income (red-keep, wood) = 3 for red; 3 + 2 income = 5 for blue.
        assertEquals(3L, t2.faction(RED).resources().get("gold"));
        assertEquals(5L, d.view(BOB).faction(BLUE).resources().get("gold"));
    }

    // ------------------------------------------------------------------ turn 2

    private static EntityId turnTwo(CampaignDriver d) {
        // The recruited warband got a generated id. Read it from the view, as a UI would.
        Entity recruit = d.view(ALICE).entitiesOf(RED).stream()
                .filter(e -> !e.id().equals(RED_W1)).findFirst().orElseThrow();
        assertEquals(RED_KEEP, recruit.at());

        // STALE: a command composed for turn 1 is refused now rather than applied to a different situation.
        rejected(d.submit(ALICE, 1, "orders", new LockIn()), "command.stale_phase");

        // Both sides send a warband into the ford. Orders are simultaneous: neither knows about the other.
        accepted(d.submit(ALICE, new MoveUnit(RED_W1, List.of(FORD))));
        accepted(d.submit(ALICE, new MoveUnit(recruit.id(), List.of(WOOD))));
        accepted(d.submit(BOB, new MoveUnit(BLUE_W1, List.of(FORD))));
        accepted(d.submit(ALICE, new LockIn()));
        accepted(d.submit(BOB, new LockIn()));

        // Everyone has locked in, so the orders resolved. Two factions ended up in one place: a pending
        // item now blocks the campaign until the battle has been played at the table.
        PlayerView afterOrders = d.view(ALICE);
        assertEquals("battles", afterOrders.phase());
        assertEquals(1, afterOrders.pending().size());
        PendingItem battle = afterOrders.pending().get(0);
        assertEquals("battle", battle.kind());
        assertEquals("ford", battle.data().get("location"));

        // Players report what happened on the table. Their reports disagree, so the GM has to rule.
        accepted(d.submit(ALICE, new ReportOutcome(battle.id(), "red")));
        accepted(d.submit(BOB, new ReportOutcome(battle.id(), "blue")));
        assertTrue(d.view(GM).pending().get(0).disputed());
        rejected(d.submit(BOB, new Adjudicate(battle.id(), "blue")), "command.forbidden");
        accepted(d.submit(GM, new Adjudicate(battle.id(), "red")));

        // The ruling triggered the ruleset's reaction (loser destroyed, ford changes hands), the battles phase
        // completed, upkeep paid income, and turn 3 began, all inside that one GM command.
        PlayerView t3 = d.view(ALICE);
        assertEquals(3, t3.turn());
        assertEquals("orders", t3.phase());
        assertTrue(t3.entity(BLUE_W1).isEmpty(), "the losing warband is gone");
        assertEquals(RED, t3.location(FORD).owner());
        // red: 3 + 3 income (red-keep, wood, ford) = 6; blue: 5 + 2 income (blue-keep, hill) = 7.
        assertEquals(6L, t3.faction(RED).resources().get("gold"));
        assertEquals(7L, d.view(BOB).faction(BLUE).resources().get("gold"));
        return recruit.id();
    }

    // ------------------------------------------------------------------ turn 3

    private static void turnThree(CampaignDriver d, EntityId recruit) {
        // GROWTH: Bob extends the map next to a location he holds. The world graph grows during play.
        accepted(d.submit(BOB, new PlaceTile(HILL, LocationId.of("marsh"), "marsh")));
        // Alice takes the mine: her fourth location, which is the victory condition.
        accepted(d.submit(ALICE, new MoveUnit(recruit, List.of(MINE))));
        accepted(d.submit(ALICE, new LockIn()));
        accepted(d.submit(BOB, new LockIn()));

        PlayerView end = d.view(ALICE);
        assertTrue(end.ended());
        assertEquals(RED, end.winner());
        assertEquals(7, end.locations().size(), "the new tile exists");
        assertTrue(end.adjacency().get(LocationId.of("marsh")).contains(HILL));

        // A finished campaign accepts nothing more.
        rejected(d.submit(ALICE, new LockIn()), "campaign.ended");
        assertFalse(d.events(GM, 0).isEmpty());
    }

    // ------------------------------------------------------------------ helpers

    /** DETERMINISM: throw the in-memory campaign away, rebuild it from the stored events, compare digests. */
    private static void replayMatchesLive(CampaignDriver d, String moment) {
        String live = d.fingerprint();
        d.restart();
        assertEquals(live, d.fingerprint(), "replay must reproduce the live state " + moment);
    }

    private static void accepted(SubmitResult r) {
        assertTrue(r.accepted(), "expected the command to be accepted but it was rejected: " + r.reasons());
    }

    private static void rejected(SubmitResult r, String code) {
        assertFalse(r.accepted(), "expected the command to be rejected with " + code);
        assertTrue(r.hasReason(code), "expected reason " + code + " but got " + r.reasons());
    }
}
