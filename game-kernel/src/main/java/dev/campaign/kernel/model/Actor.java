package dev.campaign.kernel.model;

import dev.campaign.kernel.id.PlayerId;

/** Who is issuing a command. Authentication happens outside the kernel; the kernel only authorises. */
public record Actor(PlayerId player, Role role) {
    public static Actor asPlayer(String id) {
        return new Actor(PlayerId.of(id), Role.PLAYER);
    }

    public static Actor asGm(String id) {
        return new Actor(PlayerId.of(id), Role.GM);
    }

    public boolean isGm() {
        return role == Role.GM;
    }
}
