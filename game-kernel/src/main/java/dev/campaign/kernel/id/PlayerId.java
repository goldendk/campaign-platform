package dev.campaign.kernel.id;

import java.util.Objects;

public record PlayerId(String value) implements Id, Comparable<PlayerId> {
    public PlayerId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("blank PlayerId");
    }

    public static PlayerId of(String value) {
        return new PlayerId(value);
    }

    @Override
    public int compareTo(PlayerId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
