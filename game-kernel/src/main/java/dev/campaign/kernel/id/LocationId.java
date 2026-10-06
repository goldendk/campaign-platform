package dev.campaign.kernel.id;

import java.util.Objects;

public record LocationId(String value) implements Id, Comparable<LocationId> {
    public LocationId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("blank LocationId");
    }

    public static LocationId of(String value) {
        return new LocationId(value);
    }

    @Override
    public int compareTo(LocationId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
