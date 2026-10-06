package dev.campaign.kernel.api;

import java.util.List;

/** Phase hook: runs on entering a phase, or after a phase's orders were resolved. Pure function of state. */
@FunctionalInterface
public interface Hook {
    List<Emission> run(Ctx ctx);
}
