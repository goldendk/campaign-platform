package dev.campaign.kernel.api;

import dev.campaign.kernel.model.Validation;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Registration record binding a command type to its handler plus the rules about when/who/how. */
public record CommandSpec<C extends Command>(
        Class<C> type, Mode mode, Who who, Set<String> phases, int priority, CommandHandler<C> handler) {

    public enum Mode {
        /** Held privately until the ORDERS phase resolves, then executed in priority order. */
        STAGED,
        /** Executed (and visible) the moment it is submitted. */
        IMMEDIATE
    }

    public enum Who { PLAYER, GM }

    public CommandSpec {
        phases = Set.copyOf(new TreeSet<>(phases));
    }

    public static <C extends Command> CommandSpec<C> staged(Class<C> type, CommandHandler<C> handler, int priority, String... phases) {
        return new CommandSpec<>(type, Mode.STAGED, Who.PLAYER, Set.of(phases), priority, handler);
    }

    public static <C extends Command> CommandSpec<C> immediate(Class<C> type, CommandHandler<C> handler, String... phases) {
        return new CommandSpec<>(type, Mode.IMMEDIATE, Who.PLAYER, Set.of(phases), 0, handler);
    }

    public CommandSpec<C> gmOnly() {
        return new CommandSpec<>(type, mode, Who.GM, phases, priority, handler);
    }

    public String id() {
        return type.getSimpleName();
    }

    public boolean allowedIn(String phase) {
        return phases.isEmpty() || phases.contains(phase);
    }

    public Validation validate(Ctx ctx, Command command) {
        return handler.validate(ctx, type.cast(command));
    }

    public List<Emission> decide(Ctx ctx, Command command) {
        return handler.decide(ctx, type.cast(command));
    }
}
