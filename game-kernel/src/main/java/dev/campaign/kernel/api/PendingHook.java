package dev.campaign.kernel.api;

import dev.campaign.kernel.model.PendingItem;

import java.util.List;

/** Reaction to a pending item being resolved (agreed by the parties, or ruled by the GM). */
@FunctionalInterface
public interface PendingHook {
    List<Emission> run(Ctx ctx, PendingItem item, String outcome);
}
