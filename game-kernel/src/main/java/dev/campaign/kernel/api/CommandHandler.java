package dev.campaign.kernel.api;

import dev.campaign.kernel.model.Validation;

import java.util.List;

/**
 * The whole implementation of a command. Both methods must be pure functions of (state, command, ctx.random()):
 * no clock, no static randomness, no I/O. That is what makes replay and the conformance suite work.
 */
public interface CommandHandler<C extends Command> {
    /** Why this command is not legal right now. Called when the command is submitted and again when it executes. */
    Validation validate(Ctx ctx, C command);

    /** The events that result. Only called after validate() succeeded. */
    List<Emission> decide(Ctx ctx, C command);
}
