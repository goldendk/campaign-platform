package dev.campaign.kernel.command;

import dev.campaign.kernel.api.Command;
import dev.campaign.kernel.api.CommandHandler;
import dev.campaign.kernel.api.CommandSpec;
import dev.campaign.kernel.api.Ctx;
import dev.campaign.kernel.api.Emission;
import dev.campaign.kernel.api.PhaseKind;
import dev.campaign.kernel.api.RulesetRegistry;
import dev.campaign.kernel.event.CoreEvent.FactionLockedIn;
import dev.campaign.kernel.event.CoreEvent.OutcomeAdjudicated;
import dev.campaign.kernel.event.CoreEvent.OutcomeReported;
import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.model.PendingItem;
import dev.campaign.kernel.model.Validation;

import java.util.List;
import java.util.Optional;

/**
 * Commands every ruleset gets for free. They also serve as the smallest possible examples of the
 * "record + handler" pattern: each command below is a record and a handler of a dozen lines.
 */
public final class CoreCommands {
    private CoreCommands() {}

    /** "I am done planning." When every faction has locked in, the phase resolves. */
    public record LockIn() implements Command {}

    /** A party reports the outcome of a pending item (e.g. who won the tabletop battle). */
    public record ReportOutcome(String pending, String outcome) implements Command {}

    /** The GM rules on a pending item, typically after the parties' reports disagreed. */
    public record Adjudicate(String pending, String outcome) implements Command {}

    public static void register(RulesetRegistry registry) {
        registry.command(CommandSpec.immediate(LockIn.class, new LockInHandler()));
        registry.command(CommandSpec.immediate(ReportOutcome.class, new ReportOutcomeHandler()));
        registry.command(CommandSpec.immediate(Adjudicate.class, new AdjudicateHandler()).gmOnly());
    }

    static final class LockInHandler implements CommandHandler<LockIn> {
        @Override
        public Validation validate(Ctx ctx, LockIn command) {
            if (ctx.phase() == null || ctx.phase().kind() != PhaseKind.ORDERS) return Validation.fail("orders.not_open");
            FactionId faction = ctx.faction().orElseThrow();
            if (ctx.state().lockedIn().contains(faction)) return Validation.fail("orders.already_locked");
            return Validation.ok();
        }

        @Override
        public List<Emission> decide(Ctx ctx, LockIn command) {
            return List.of(Emission.publicly(new FactionLockedIn(ctx.faction().orElseThrow(), false)));
        }
    }

    static final class ReportOutcomeHandler implements CommandHandler<ReportOutcome> {
        @Override
        public Validation validate(Ctx ctx, ReportOutcome c) {
            Optional<PendingItem> found = ctx.state().pending(c.pending());
            if (found.isEmpty()) return Validation.fail("pending.unknown", "pending", c.pending());
            PendingItem p = found.get();
            FactionId faction = ctx.faction().orElseThrow();
            if (p.resolved()) return Validation.fail("pending.closed", "pending", p.id());
            if (p.disputed()) return Validation.fail("pending.disputed", "pending", p.id());
            if (!p.parties().contains(faction)) return Validation.fail("pending.not_party", "pending", p.id());
            if (p.reports().containsKey(faction)) return Validation.fail("pending.already_reported", "pending", p.id());
            if (!p.options().contains(c.outcome())) return Validation.fail("pending.bad_outcome", "outcome", c.outcome());
            return Validation.ok();
        }

        @Override
        public List<Emission> decide(Ctx ctx, ReportOutcome c) {
            return List.of(Emission.publicly(new OutcomeReported(c.pending(), ctx.faction().orElseThrow(), c.outcome())));
        }
    }

    static final class AdjudicateHandler implements CommandHandler<Adjudicate> {
        @Override
        public Validation validate(Ctx ctx, Adjudicate c) {
            Optional<PendingItem> found = ctx.state().pending(c.pending());
            if (found.isEmpty()) return Validation.fail("pending.unknown", "pending", c.pending());
            PendingItem p = found.get();
            if (p.resolved()) return Validation.fail("pending.closed", "pending", p.id());
            if (!p.options().contains(c.outcome())) return Validation.fail("pending.bad_outcome", "outcome", c.outcome());
            return Validation.ok();
        }

        @Override
        public List<Emission> decide(Ctx ctx, Adjudicate c) {
            return List.of(Emission.publicly(new OutcomeAdjudicated(c.pending(), c.outcome())));
        }
    }
}
