package dev.campaign.kernel.api;

import java.time.Duration;
import java.util.Objects;

public record PhaseDef(String id, PhaseKind kind, PhaseMode mode, Duration deadline) {
    public PhaseDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(mode, "mode");
    }

    public static PhaseDef orders(String id, Duration deadline) {
        return new PhaseDef(id, PhaseKind.ORDERS, PhaseMode.SIMULTANEOUS, deadline);
    }

    public static PhaseDef pending(String id) {
        return new PhaseDef(id, PhaseKind.PENDING, PhaseMode.SIMULTANEOUS, null);
    }

    public static PhaseDef automatic(String id) {
        return new PhaseDef(id, PhaseKind.AUTOMATIC, PhaseMode.SIMULTANEOUS, null);
    }
}
