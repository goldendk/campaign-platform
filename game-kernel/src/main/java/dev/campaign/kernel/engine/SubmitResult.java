package dev.campaign.kernel.engine;

import dev.campaign.kernel.model.Reason;

import java.util.List;

/** Outcome of submit()/tick(). A rejection is a normal result, not an exception. */
public record SubmitResult(boolean accepted, long version, long newEvents, List<Reason> reasons) {
    public SubmitResult {
        reasons = List.copyOf(reasons);
    }

    public static SubmitResult accepted(long version, long newEvents) {
        return new SubmitResult(true, version, newEvents, List.of());
    }

    public static SubmitResult rejected(List<Reason> reasons) {
        return new SubmitResult(false, -1, 0, reasons);
    }

    public static SubmitResult rejected(String code, String... keyValuePairs) {
        return rejected(List.of(Reason.of(code, keyValuePairs)));
    }

    public boolean hasReason(String code) {
        return reasons.stream().anyMatch(r -> r.code().equals(code));
    }
}
