package dev.campaign.kernel.model;

import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.util.Sorted;

import java.util.Set;

/** Who may see an event in full. The GM always can. Everyone else gets the redacted form, if there is one. */
public record Audience(boolean everyone, Set<FactionId> factions) {
    public Audience {
        factions = Sorted.set(factions);
    }

    public static Audience all() {
        return new Audience(true, Set.of());
    }

    public static Audience only(FactionId... factions) {
        return new Audience(false, Set.of(factions));
    }

    public static Audience gmOnly() {
        return new Audience(false, Set.of());
    }

    public boolean canSee(Actor viewer, FactionId viewerFaction) {
        return viewer.isGm() || everyone || (viewerFaction != null && factions.contains(viewerFaction));
    }
}
