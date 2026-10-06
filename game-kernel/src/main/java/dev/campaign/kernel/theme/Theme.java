package dev.campaign.kernel.theme;

import java.util.Optional;

/** Vocabulary for one setting/language: event templates, reason texts and term translations. */
public interface Theme {
    Optional<String> lookup(String key);

    default String term(String key) {
        return lookup("term." + key).orElse(key);
    }
}
