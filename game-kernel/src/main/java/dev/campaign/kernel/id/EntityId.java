package dev.campaign.kernel.id;

import java.util.Objects;

public record EntityId(String value) implements Id, Comparable<EntityId> {
    public EntityId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("blank EntityId");
    }

    public static EntityId of(String value) {
        return new EntityId(value);
    }

    @Override
    public int compareTo(EntityId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
