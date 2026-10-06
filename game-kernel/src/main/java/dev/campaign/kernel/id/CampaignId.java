package dev.campaign.kernel.id;

import java.util.Objects;

public record CampaignId(String value) implements Id, Comparable<CampaignId> {
    public CampaignId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("blank CampaignId");
    }

    public static CampaignId of(String value) {
        return new CampaignId(value);
    }

    @Override
    public int compareTo(CampaignId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
