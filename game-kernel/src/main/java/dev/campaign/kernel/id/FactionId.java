package dev.campaign.kernel.id;

import java.util.Objects;

public record FactionId(String value) implements Id, Comparable<FactionId> {
    public FactionId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("blank FactionId");
    }

    public static FactionId of(String value) {
        return new FactionId(value);
    }

    @Override
    public int compareTo(FactionId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
