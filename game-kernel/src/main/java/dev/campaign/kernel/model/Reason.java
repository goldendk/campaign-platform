package dev.campaign.kernel.model;

import dev.campaign.kernel.util.Sorted;

import java.util.Map;
import java.util.TreeMap;

/**
 * Why something was refused: a stable code plus arguments. Never prose. Themes turn "move.too_far" into
 * "The army cannot march more than 2 steps" or "The freighter lacks the fuel for 3 jumps".
 */
public record Reason(String code, Map<String, String> args) {
    public Reason {
        args = Sorted.map(args);
    }

    public static Reason of(String code, String... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) throw new IllegalArgumentException("key/value pairs expected");
        Map<String, String> args = new TreeMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) args.put(keyValuePairs[i], keyValuePairs[i + 1]);
        return new Reason(code, args);
    }
}
