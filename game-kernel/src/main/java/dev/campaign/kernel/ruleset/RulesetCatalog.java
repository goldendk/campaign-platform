package dev.campaign.kernel.ruleset;

import dev.campaign.kernel.api.Ruleset;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;

/** Finds every ruleset on the classpath via META-INF/services. No framework needed. */
public final class RulesetCatalog {
    private RulesetCatalog() {}

    public static Map<String, Ruleset> discover() {
        Map<String, Ruleset> found = new TreeMap<>();
        for (Ruleset r : ServiceLoader.load(Ruleset.class)) {
            if (found.put(r.id(), r) != null) throw new IllegalStateException("Duplicate ruleset id: " + r.id());
        }
        return found;
    }
}
