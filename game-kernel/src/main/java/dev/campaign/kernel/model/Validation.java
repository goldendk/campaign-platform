package dev.campaign.kernel.model;

import java.util.ArrayList;
import java.util.List;

public final class Validation {
    private static final Validation OK = new Validation(List.of());

    private final List<Reason> reasons;

    private Validation(List<Reason> reasons) {
        this.reasons = List.copyOf(reasons);
    }

    public static Validation ok() {
        return OK;
    }

    public static Validation fail(String code, String... keyValuePairs) {
        return new Validation(List.of(Reason.of(code, keyValuePairs)));
    }

    public Validation and(Validation other) {
        if (other.isValid()) return this;
        if (isValid()) return other;
        List<Reason> merged = new ArrayList<>(reasons);
        merged.addAll(other.reasons);
        return new Validation(merged);
    }

    public boolean isValid() {
        return reasons.isEmpty();
    }

    public List<Reason> reasons() {
        return reasons;
    }
}
