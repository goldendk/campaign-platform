package dev.campaign.kernel.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Determinism helper. Java's immutable Set.of/Map.of iterate in a per-JVM randomised order, which would make
 * replay and conflict resolution non-deterministic. Everything stored in state or events is copied through here
 * so that iteration order is always the natural order of the elements.
 */
public final class Sorted {
    private Sorted() {}

    public static <T extends Comparable<? super T>> Set<T> set(Collection<? extends T> in) {
        TreeSet<T> copy = new TreeSet<>();
        if (in != null) copy.addAll(in);
        return Collections.unmodifiableSortedSet(copy);
    }

    public static <K extends Comparable<? super K>, V> Map<K, V> map(Map<? extends K, ? extends V> in) {
        TreeMap<K, V> copy = new TreeMap<>();
        if (in != null) copy.putAll(in);
        return Collections.unmodifiableSortedMap(copy);
    }
}
