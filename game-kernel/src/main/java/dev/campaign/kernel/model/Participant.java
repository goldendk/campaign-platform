package dev.campaign.kernel.model;

import dev.campaign.kernel.id.FactionId;
import dev.campaign.kernel.id.PlayerId;

public record Participant(PlayerId player, FactionId faction, String name) {
    public static Participant of(String player, String faction, String name) {
        return new Participant(PlayerId.of(player), FactionId.of(faction), name);
    }
}
